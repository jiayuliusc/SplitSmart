const assert = require('node:assert/strict');
const {test} = require('node:test');
const {randomUUID, randomInt} = require('node:crypto');
const {initializeApp} = require('firebase-admin/app');
const {getFirestore} = require('firebase-admin/firestore');
assert.equal(process.env.GCLOUD_PROJECT, 'demo-splitsmart');
assert.match(process.env.FIRESTORE_EMULATOR_HOST || '', /^(127\.0\.0\.1|localhost):8080$/);
assert.match(process.env.FIREBASE_AUTH_EMULATOR_HOST || '', /^(127\.0\.0\.1|localhost):9099$/);
const db = getFirestore(initializeApp({projectId:'demo-splitsmart'}, 'settlement-tests'));
const id = () => ({schemaVersion:1, requestId:randomUUID()});
async function call(actor, name, data) {
 const response = await fetch(`http://127.0.0.1:5001/demo-splitsmart/us-central1/${name}`, {method:'POST',headers:{'Content-Type':'application/json',...(actor?{Authorization:`Bearer ${actor.token}`}:{})},body:JSON.stringify({data})});
 const body = await response.json(); return body.error ? {error:body.error.details?.code || body.error.status} : {value:body.result};
}
async function ok(actor,name,data) { const r=await call(actor,name,data); assert.ok(r.value,JSON.stringify(r)); return r.value; }
async function denied(actor,name,data,code) { assert.equal((await call(actor,name,data)).error,code); }
async function user(profile=true) {
 const email=`settle-${randomUUID()}@usc.edu`;
 const response=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password:'test-password',returnSecureToken:true})});
 const a=await response.json(); assert.ok(a.idToken); const result={uid:a.localId,token:a.idToken,email};
 if(profile) await ok(result,'completeProfile',{...id(),profile:{name:'Payment user',uscId:String(randomInt(1000000000,9999999999))}});
 return result;
}
async function fixture() {
 const owner=await user(), member=await user(), outsider=await user(), unregistered=await user(false);
 const group=(await ok(owner,'createGroup',{...id(),draft:{name:'Payment group'}})).value;
 await ok(owner,'addGroupMember',{...id(),groupId:group.groupId,expectedRevision:'1',email:member.email});
 const f={owner,member,outsider,unregistered,groupId:group.groupId,revision:2};
 f.payload=(extra={})=>({...id(),groupId:f.groupId,expectedRevision:String(f.revision),...extra});
 f.expense=async(amount='1200')=>{const r=await ok(owner,'createExpense',f.payload({localDate:'2026-11-04',draft:{title:'Lunch',amountCents:amount,payerId:owner.uid,participantIds:[owner.uid,member.uid].sort(),splitMethod:'EQUAL',category:'FOOD',splitInputs:[]}}));f.revision=Number(r.groupRevision);return r.value;};
 f.create=(amount='400',extra={})=>f.payload({localDate:'2026-11-04',draft:{payeeId:owner.uid,amountCents:amount},...extra});
 f.mutate=async(actor,name,payload)=>{const r=await ok(actor,name,payload);f.revision=Number(r.groupRevision);return r;};
 return f;
}
test('settlement callable actor, authorization, receipts and replacement semantics',async t=>{
 const f=await fixture(); const expense=await f.expense();
 await t.test('strict shape, auth, membership and registered profile',async()=>{
  await denied(null,'createSettlement',f.create(),'UNAUTHENTICATED');
  await denied(f.outsider,'createSettlement',f.create(),'PERMISSION_DENIED');
  await denied(f.unregistered,'createSettlement',f.create(),'NOT_FOUND');
  await denied(f.member,'createSettlement',f.create('400',{payerId:f.owner.uid}),'VALIDATION');
  await denied(f.member,'createSettlement',f.create('400',{draft:{payeeId:f.owner.uid,amountCents:'400',creatorId:f.owner.uid}}),'VALIDATION');
  await denied(f.member,'createSettlement',f.create('400',{localDate:'2026-02-30'}),'VALIDATION');
  await denied(f.member,'createSettlement',f.create('0'),'OUT_OF_RANGE');
  await denied(f.member,'createSettlement',f.create('601'),'VALIDATION');
  await denied(f.member,'createSettlement',f.create('400',{draft:{payeeId:f.member.uid,amountCents:'400'}}),'VALIDATION');
 });
 const request=f.create(); const created=await f.mutate(f.member,'createSettlement',request); const payment=created.value;
 assert.equal(payment.payerId,f.member.uid);assert.equal(payment.creatorId,f.member.uid);assert.equal(payment.amountCents,'400');
 assert.equal((await db.doc(`groups/${f.groupId}/settlements/${payment.settlementId}`).get()).get('amountCents'),400);
 await t.test('success replay and mismatch do not bump revision',async()=>{
  assert.deepEqual(await ok(f.member,'createSettlement',request),created);
  await denied(f.member,'createSettlement',{...request,draft:{...request.draft,amountCents:'399'}},'IDEMPOTENCY_MISMATCH');
 });
 await t.test('only creator may edit or delete',async()=>{
  await denied(f.owner,'updateSettlement',f.payload({settlementId:payment.settlementId,draft:{payeeId:f.owner.uid,amountCents:'300'}}),'PERMISSION_DENIED');
  await denied(f.owner,'deleteSettlement',f.payload({settlementId:payment.settlementId}),'PERMISSION_DENIED');
 });
 await t.test('replacement excludes original and preserves metadata',async()=>{
  const r=await f.mutate(f.member,'updateSettlement',f.payload({settlementId:payment.settlementId,draft:{payeeId:f.owner.uid,amountCents:'600',note:'complete'}}));
  assert.equal(r.value.amountCents,'600');assert.deepEqual(r.value.createdAt,payment.createdAt);assert.equal(r.value.localDate,payment.localDate);
  await denied(f.member,'createSettlement',f.create('1'),'VALIDATION');
 });
 await t.test('expense correction preserves credit and rejects note-only edits without debt',async()=>{
  await f.mutate(f.owner,'deleteExpense',f.payload({expenseId:expense.expenseId}));
  const ledger=(await ok(f.member,'getGroupLedger',{schemaVersion:1,groupId:f.groupId})).ledger;
  assert.equal(ledger.expenses.length,0);assert.equal(ledger.settlements[0].amountCents,'600');
  await denied(f.member,'updateSettlement',f.payload({settlementId:payment.settlementId,draft:{payeeId:f.owner.uid,amountCents:'600',note:'historical'}}),'VALIDATION');
  await f.mutate(f.member,'deleteSettlement',f.payload({settlementId:payment.settlementId}));
 });
 await t.test('error receipt stays frozen after state changes and group deletion',async()=>{
  const bad=f.create('1'); await denied(f.member,'createSettlement',bad,'VALIDATION');
  await f.expense(); await denied(f.member,'createSettlement',bad,'VALIDATION');
  await db.doc(`groups/${f.groupId}`).delete();
  assert.deepEqual(await ok(f.member,'createSettlement',request),created);
  await denied(f.member,'createSettlement',bad,'VALIDATION');
 });
});
test('concurrent payments serialize and unsupported ledger blocks every writer',async()=>{
 const f=await fixture(); await f.expense(); const a=f.create(),b=f.create();
 const result=await Promise.all([call(f.member,'createSettlement',a),call(f.member,'createSettlement',b)]);
 assert.equal(result.filter(r=>r.value).length,1);assert.equal(result.find(r=>r.error).error,'CONFLICT');f.revision++;
 await denied(f.member,'createSettlement',f.create('400'),'VALIDATION');
 const existing=(await db.collection(`groups/${f.groupId}/settlements`).get()).docs[0];
 await existing.ref.update({schemaVersion:2});
 await denied(f.owner,'createExpense',f.payload({localDate:'2026-11-04',draft:{title:'Bad ledger',amountCents:'100',payerId:f.owner.uid,participantIds:[f.owner.uid,f.member.uid].sort(),splitMethod:'EQUAL',category:'OTHER',splitInputs:[]}}),'INTERNAL');
 await denied(f.member,'getGroupLedger',{schemaVersion:1,groupId:f.groupId},'INTERNAL');
});
test('exact pair and aggregate bounds reject mutations without changing ledger',async()=>{
 const f=await fixture(); await f.expense('9007199254740991');
 await denied(f.owner,'createExpense',f.payload({localDate:'2026-11-04',draft:{title:'Overflow',amountCents:'9007199254740991',payerId:f.owner.uid,participantIds:[f.owner.uid,f.member.uid].sort(),splitMethod:'EXACT',category:'OTHER',splitInputs:[{participantId:f.owner.uid,amountCents:'0'},{participantId:f.member.uid,amountCents:'9007199254740991'}].sort((a,b)=>a.participantId.localeCompare(b.participantId))}}),'OUT_OF_RANGE');
 const ledger=(await ok(f.owner,'getGroupLedger',{schemaVersion:1,groupId:f.groupId})).ledger;assert.equal(ledger.expenses.length,1);
});
test('settlement parties are fixed, and expense edits keep recorded payments',async()=>{
 const f=await fixture(); const other=await user();
 await f.mutate(f.owner,'addGroupMember',f.payload({email:other.email}));
 const expense=await f.expense(); const payment=(await f.mutate(f.member,'createSettlement',f.create())).value;
 await f.mutate(other,'createExpense',f.payload({localDate:'2026-11-04',draft:{title:'Other debt',amountCents:'300',payerId:other.uid,participantIds:[other.uid,f.member.uid].sort(),splitMethod:'EQUAL',category:'OTHER',splitInputs:[]}}));
 // The member now really owes `other`, but the payee still cannot change.
 await denied(f.member,'updateSettlement',f.payload({settlementId:payment.settlementId,draft:{payeeId:other.uid,amountCents:'100'}}),'VALIDATION');
 await f.mutate(f.member,'updateSettlement',f.payload({settlementId:payment.settlementId,draft:{payeeId:f.owner.uid,amountCents:'150'}}));
 const revised=await f.mutate(f.owner,'updateExpense',f.payload({expenseId:expense.expenseId,draft:{title:'Corrected',amountCents:'100',payerId:f.owner.uid,participantIds:[f.owner.uid,f.member.uid].sort(),splitMethod:'EQUAL',category:'FOOD',splitInputs:[]}}));
 assert.equal(revised.value.amountCents,'100');
 const ledger=(await ok(f.member,'getGroupLedger',{schemaVersion:1,groupId:f.groupId})).ledger;
 assert.equal(ledger.settlements[0].payeeId,f.owner.uid);assert.equal(ledger.settlements[0].amountCents,'150');
 await f.mutate(f.member,'deleteSettlement',f.payload({settlementId:payment.settlementId}));
 assert.equal((await ok(f.member,'getGroupLedger',{schemaVersion:1,groupId:f.groupId})).ledger.settlements.length,0);
});
test('malformed stored records stop all financial mutations without partial repair',async()=>{
 const f=await fixture();const expense=await f.expense();
 const ref=db.doc(`groups/${f.groupId}/expenses/${expense.expenseId}`);
 for(const patch of [{currency:'EUR'},{payerId:f.outsider.uid},{localDate:'2026-13-01'},{shares:[{participantId:f.owner.uid,amountCents:1200}]},{extra:true}]) {
  const original=(await ref.get()).data();await ref.set({...original,...patch});
  await denied(f.member,'createSettlement',f.create('1'),'INTERNAL');
  await denied(f.owner,'deleteExpense',f.payload({expenseId:expense.expenseId}),'INTERNAL');
  await ref.set(original);
 }
});
test('either side may record a payment; parties are fixed and the ledger stays readable',async t=>{
 const f=await fixture(); const other=await user();
 await f.mutate(f.owner,'addGroupMember',f.payload({email:other.email}));
 await f.expense();   // owner paid $12.00 split with member: member owes owner $6.00
 const received=(draft={})=>f.payload({localDate:'2026-11-04',draft:{payerId:f.member.uid,payeeId:f.owner.uid,amountCents:'500',...draft}});

 await t.test('only a party to the payment may record it',async()=>{
  await denied(other,'createSettlement',received(),'VALIDATION');
 });
 await t.test('recipient-created amounts are capped by the payer debt',async()=>{
  await denied(f.owner,'createSettlement',received({amountCents:'601'}),'VALIDATION');
  await denied(f.owner,'createSettlement',f.payload({localDate:'2026-11-04',draft:{payerId:f.owner.uid,payeeId:f.member.uid,amountCents:'1'}}),'VALIDATION');
 });

 const created=(await f.mutate(f.owner,'createSettlement',received())).value;
 assert.equal(created.payerId,f.member.uid);assert.equal(created.payeeId,f.owner.uid);assert.equal(created.creatorId,f.owner.uid);
 // Stored creator differs from payer: the ledger must still load for every member.
 const ledger=(await ok(f.member,'getGroupLedger',{schemaVersion:1,groupId:f.groupId})).ledger;
 assert.equal(ledger.settlements.length,1);assert.equal(ledger.settlements[0].creatorId,f.owner.uid);

 await t.test('the debtor cannot edit a recipient-created record',async()=>{
  await denied(f.member,'updateSettlement',f.payload({settlementId:created.settlementId,draft:{payerId:f.member.uid,payeeId:f.owner.uid,amountCents:'100'}}),'PERMISSION_DENIED');
 });
 await t.test('payer and payee cannot change on edit',async()=>{
  await denied(f.owner,'updateSettlement',f.payload({settlementId:created.settlementId,draft:{payerId:other.uid,payeeId:f.owner.uid,amountCents:'100'}}),'VALIDATION');
  await denied(f.owner,'updateSettlement',f.payload({settlementId:created.settlementId,draft:{payerId:f.member.uid,payeeId:other.uid,amountCents:'100'}}),'VALIDATION');
 });

 const edited=(await f.mutate(f.owner,'updateSettlement',f.payload({settlementId:created.settlementId,draft:{payerId:f.member.uid,payeeId:f.owner.uid,amountCents:'600',note:'cash'}}))).value;
 assert.equal(edited.amountCents,'600');assert.equal(edited.payerId,f.member.uid);assert.equal(edited.creatorId,f.owner.uid);
 // Omitting payerId on edit keeps the stored payer.
 const kept=(await f.mutate(f.owner,'updateSettlement',f.payload({settlementId:created.settlementId,draft:{payeeId:f.owner.uid,amountCents:'300'}}))).value;
 assert.equal(kept.payerId,f.member.uid);assert.equal(kept.amountCents,'300');

 await f.mutate(f.owner,'deleteSettlement',f.payload({settlementId:created.settlementId}));
 assert.equal((await ok(f.member,'getGroupLedger',{schemaVersion:1,groupId:f.groupId})).ledger.settlements.length,0);
});
