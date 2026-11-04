const assert = require('node:assert/strict');
const {test} = require('node:test');
const {randomUUID} = require('node:crypto');
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, Timestamp} = require('firebase-admin/firestore');

// Never run these synthetic account/data tests against a real project.
assert.equal(process.env.GCLOUD_PROJECT, 'demo-splitsmart');
assert.match(process.env.FIRESTORE_EMULATOR_HOST || '', /^(127\.0\.0\.1|localhost):8080$/);
assert.match(process.env.FIREBASE_AUTH_EMULATOR_HOST || '', /^(127\.0\.0\.1|localhost):9099$/);
const db = getFirestore(initializeApp({projectId: 'demo-splitsmart'}, 'ledger-tests'));
const docs = 'http://127.0.0.1:8080/v1/projects/demo-splitsmart/databases/(default)/documents';
const suffix = randomUUID().replaceAll('-', '').slice(0, 10);
const id = () => ({schemaVersion: 1, requestId: randomUUID()});

async function auth(path, data) {
  const response = await fetch(`http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:${path}?key=demo-key`, {
    method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(data),
  });
  return {status: response.status, data: await response.json()};
}
async function signup(label, domain = 'usc.edu') {
  const email = `${label}-${suffix}@${domain}`;
  const result = await auth('signUp', {email, password:'testing-password', returnSecureToken:true});
  assert.equal(result.status, 200);
  return {uid:result.data.localId, token:result.data.idToken, email};
}
async function call(actor, name, data) {
  const response = await fetch(`http://127.0.0.1:5001/demo-splitsmart/us-central1/${name}`, {
    method:'POST', headers:{'Content-Type':'application/json', ...(actor ? {Authorization:`Bearer ${actor.token}`} : {})},
    body:JSON.stringify({data}),
  });
  const body = await response.json();
  return body.error ? {error:body.error.details?.code || body.error.status, body} : {value:body.result};
}
async function ok(actor, name, data) { const result = await call(actor,name,data); assert.ok(result.value, JSON.stringify(result)); return result.value; }
async function denied(actor, name, data, code) { assert.equal((await call(actor,name,data)).error,code); }

test('complete authoritative ledger includes settlements and rejects unsupported schemas', async t => {
  const owner = await signup('ledger-owner'); const member = await signup('ledger-member'); const outsider = await signup('ledger-outsider');
  for (const [index, actor] of [owner, member, outsider].entries()) {
    await ok(actor, 'completeProfile', {...id(), profile:{name:`Ledger user ${index}`, uscId:`9${suffix.slice(0, 7).replace(/[a-f]/g, '1')}${index}0`}});
  }
  const created = await ok(owner, 'createGroup', {...id(), draft:{name:'Ledger fixture'}});
  const groupId = created.value.groupId;
  await ok(owner, 'addGroupMember', {...id(), groupId, expectedRevision:'1', email:member.email});
  const now = Timestamp.fromMillis(1700000000000);
  const base = {schemaVersion:1, creatorId:owner.uid, payerId:owner.uid, amountCents:123, currency:'USD', localDate:'2026-11-03', createdAt:now, updatedAt:now};
  const expense = {...base, title:'Meal', participantIds:[member.uid,owner.uid], splitMethod:'EXACT', category:'FOOD', shares:[{participantId:owner.uid,amountCents:23},{participantId:member.uid,amountCents:100}]};
  const settlement = {...base, payerId:member.uid, payeeId:owner.uid, creatorId:member.uid, note:'Paid back'};
  const parent = db.doc(`groups/${groupId}`);
  const batch = db.batch();
  for (const key of ['b', 'a']) {
    batch.set(parent.collection('expenses').doc(key), key === 'b' ? {...expense, amountCents:'123', shares:expense.shares.map(s=>({...s,amountCents:String(s.amountCents)}))} : expense);
    batch.set(parent.collection('settlements').doc(key), settlement);
  }
  batch.set(parent.collection('settlements').doc('earlier'), {...settlement, createdAt:Timestamp.fromMillis(1600000000000)});
  await batch.commit();
  const request = {schemaVersion:1, groupId};
  await t.test('authenticated member receives complete records, stable ordering and revision', async () => {
    const {ledger} = await ok(member, 'getGroupLedger', request);
    assert.equal(ledger.revision,'2'); assert.deepEqual(ledger.memberIds,[owner.uid,member.uid].sort());
    assert.deepEqual(ledger.expenses.map(e=>e.expenseId),['a','b']);
    assert.deepEqual(ledger.settlements.map(s=>s.settlementId),['earlier','a','b']);
    assert.deepEqual(ledger.settlements[1], {settlementId:'a', groupId, creatorId:member.uid, payerId:member.uid, payeeId:owner.uid, amountCents:'123', currency:'USD', note:'Paid back', localDate:'2026-11-03', createdAt:{seconds:1700000000,nanoseconds:0},updatedAt:{seconds:1700000000,nanoseconds:0}});
    assert.equal(ledger.expenses[0].amountCents,'123'); assert.deepEqual(ledger.expenses[0].shares.map(s=>s.amountCents).sort(),['100','23']);
    assert.equal(ledger.expenses[1].amountCents,'123');
  });
  await t.test('unauthorized users cannot receive financial records', async () => {
    await denied(outsider,'getGroupLedger',request,'PERMISSION_DENIED');
    await denied(null,'getGroupLedger',request,'UNAUTHENTICATED');
  });
  for (const [label, path, field, initial] of [
    ['expense total', 'expenses/a', 'amountCents', expense.amountCents],
    ['settlement total', 'settlements/a', 'amountCents', settlement.amountCents],
    ['expense share', 'expenses/a', 'shares', expense.shares],
  ]) {
    await t.test(`rejects malformed and unsupported ${label} money`, async () => {
      const ref = db.doc(`${parent.path}/${path}`);
      const invalid = [null, true, 1.5, -1, Number.MAX_SAFE_INTEGER + 1, '9007199254740992', '01', '1.0', 'oops'];
      if (field === 'amountCents') invalid.push(0, '0');
      for (const amount of invalid) {
        await ref.update({[field]:field === 'shares' ? [{...expense.shares[0],amountCents:amount},expense.shares[1]] : amount});
        await denied(owner,'getGroupLedger',request,'INTERNAL');
      }
      await ref.update({[field]:initial});
    });
  }
  for (const collection of ['expenses','settlements']) {
    await t.test(`unsupported ${collection} schema rejects the entire ledger`, async () => {
      const ref = parent.collection(collection).doc('a');
      await ref.update({schemaVersion:2});
      await denied(owner,'getGroupLedger',request,'INTERNAL');
      await ref.update({schemaVersion:1});
    });
  }
});
