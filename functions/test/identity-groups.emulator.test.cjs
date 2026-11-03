const assert = require('node:assert/strict');
const {test} = require('node:test');
const {randomUUID} = require('node:crypto');
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, Timestamp} = require('firebase-admin/firestore');
const {cleanupBatch} = require('../lib/groups/cleanup');
const {canonical} = require('../lib/infrastructure/receipts');
const {handle} = require('../lib/identity/handlers');

// Never run these synthetic account/data tests against a real project.
assert.equal(process.env.GCLOUD_PROJECT, 'demo-splitsmart');
assert.match(process.env.FIRESTORE_EMULATOR_HOST || '', /^(127\.0\.0\.1|localhost):8080$/);
assert.match(process.env.FIREBASE_AUTH_EMULATOR_HOST || '', /^(127\.0\.0\.1|localhost):9099$/);
const db = getFirestore(initializeApp({projectId: 'demo-splitsmart'}, 'feature-tests'));
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
async function read(actor, path, options = {}) {
  const response = await fetch(`${docs}/${path}`, {headers:{...(actor ? {Authorization:`Bearer ${actor.token}`} : {})}, ...options});
  return response;
}
async function waitUntil(check, description) {
  for (let i=0;i<100;i++) { if(await check()) return; await new Promise(resolve=>setTimeout(resolve,100)); }
  assert.fail(`Timed out: ${description}`);
}

test('identity, reset, authorization, idempotency and resumable group lifecycle', async t => {
  const owner = await signup('owner'); const member = await signup('member'); const outsider = await signup('outsider');
  const ownerProfile = {...id(), profile:{name:'Owner',uscId:'0012345678'}};
  await t.test('strict input and exact USC domain; incomplete Auth is recoverable', async () => {
    await denied(null,'completeProfile',ownerProfile,'UNAUTHENTICATED');
    const outside = await signup('outside','example.com');
    await denied(outside,'completeProfile',ownerProfile,'PERMISSION_DENIED');
    await denied(owner,'completeProfile',{...id(),profile:{name:'Owner',uscId:'１２３４５６７８９０'}},'VALIDATION');
    await denied(owner,'completeProfile',{...ownerProfile,creatorId:owner.uid},'VALIDATION');
    await denied(owner,'createGroup',{...id(),draft:{name:'Incomplete'}},'NOT_FOUND');
    assert.equal((await read(owner,`users/${owner.uid}`)).status,404);
    const result = await ok(owner,'completeProfile',ownerProfile);
    assert.equal(result.value.uscId,'0012345678');
    assert.deepEqual(await ok(owner,'completeProfile',ownerProfile),result);
    await denied(owner,'completeProfile',{...ownerProfile,profile:{name:'Changed',uscId:'0012345678'}},'IDEMPOTENCY_MISMATCH');
  });
  await t.test('unique email and transactional USC ID claim; recovery without recreating Auth', async () => {
    assert.equal((await auth('signUp',{email:owner.email,password:'testing-password',returnSecureToken:true})).status,400);
    const shared='0012345679';
    const requests=[{...id(),profile:{name:'Member',uscId:shared}},{...id(),profile:{name:'Outsider',uscId:shared}}];
    const results=await Promise.all([call(member,'completeProfile',requests[0]),call(outsider,'completeProfile',requests[1])]);
    assert.equal(results.filter(r=>r.value).length,1); assert.equal(results.filter(r=>r.error==='ALREADY_EXISTS').length,1);
    const loser=results[0].error ? member : outsider;
    const loserRequest=results[0].error ? requests[0] : requests[1];
    await denied(loser,'completeProfile',loserRequest,'ALREADY_EXISTS');
    await ok(loser,'completeProfile',{...id(),profile:{name:'Recovered',uscId:'0012345680'}});
    assert.equal((await read(loser,`users/${loser.uid}`)).status,200);
    assert.equal((await db.doc(`uscIdClaims/${shared}`).get()).get('uid'),results[0].value ? member.uid : outsider.uid);
  });
  await t.test('private profile and name-only update; denied direct writes', async () => {
    assert.equal((await read(member,`users/${owner.uid}`)).status,403);
    assert.equal((await read(owner,'users')).status,403);
    for(const path of ['uscIdClaims/0012345678',`mutationReceipts/${owner.uid}/requests/${ownerProfile.requestId}`]) assert.equal((await read(owner,path)).status,403);
    const update={...id(),name:'New owner name'};
    const result=await ok(owner,'updateProfileName',update);
    assert.equal(result.value.email,owner.email); assert.equal(result.value.uscId,'0012345678');
    assert.deepEqual(await ok(owner,'updateProfileName',update),result);
    await denied(owner,'updateProfileName',{...id(),name:'X',email:'changed@usc.edu'},'VALIDATION');
    assert.equal((await read(owner,`users/${owner.uid}`,{method:'PATCH',headers:{Authorization:`Bearer ${owner.token}`,'Content-Type':'application/json'},body:JSON.stringify({fields:{name:{stringValue:'Hacked'}}})})).status,403);
  });
  let group;
  await t.test('multiple groups; creator owns initial membership; idempotent creation', async () => {
    const request={...id(),draft:{name:'Shared home',description:'Test group'}};
    const first=await ok(owner,'createGroup',request); group=first.value;
    assert.equal(group.ownerId,owner.uid); assert.deepEqual(group.memberIds,[owner.uid]); assert.equal(group.revision,'1');
    assert.deepEqual(await ok(owner,'createGroup',request),first);
    const second=await ok(owner,'createGroup',{...id(),draft:{name:'Weekend'}});
    assert.notEqual(second.value.groupId,group.groupId);
    const response=await fetch(`${docs}:runQuery`,{method:'POST',headers:{Authorization:`Bearer ${owner.token}`,'Content-Type':'application/json'},body:JSON.stringify({structuredQuery:{from:[{collectionId:'groups'}],where:{compositeFilter:{op:'AND',filters:[{fieldFilter:{field:{fieldPath:'memberIds'},op:'ARRAY_CONTAINS',value:{stringValue:owner.uid}}},{fieldFilter:{field:{fieldPath:'status'},op:'EQUAL',value:{stringValue:'ACTIVE'}}}]}}}})});
    assert.equal(response.status,200); assert.equal((await response.json()).filter(row=>row.document).length,2);
    assert.equal((await read(outsider,`groups/${group.groupId}`)).status,403);
    assert.equal((await read(null,`groups/${group.groupId}`)).status,403);
  });
  await t.test('owner-only membership, registered USC email, revision and privacy', async () => {
    const scope={groupId:group.groupId,expectedRevision:'1'};
    await denied(outsider,'addGroupMember',{...id(),...scope,email:member.email},'PERMISSION_DENIED');
    await denied(owner,'addGroupMember',{...id(),...scope,email:'nope@sub.usc.edu'},'VALIDATION');
    await denied(owner,'addGroupMember',{...id(),...scope,email:`missing-${suffix}@usc.edu`},'NOT_FOUND');
    const incomplete=await signup('incomplete');
    await denied(owner,'addGroupMember',{...id(),...scope,email:incomplete.email},'NOT_FOUND');
    const addition={...id(),...scope,email:member.email};
    const result=await ok(owner,'addGroupMember',addition); group=result.value;
    const unavailableAuth = {getUserByEmail: () => { throw new Error('Auth lookup unavailable'); }};
    assert.deepEqual(await handle(db, unavailableAuth, owner, 'addGroupMember', addition), result);
    assert.deepEqual(await ok(owner,'addGroupMember',addition),result); assert.equal(group.revision,'2');
    const profiles=await ok(member,'getGroupMemberProfiles',{schemaVersion:1,groupId:group.groupId});
    assert.equal(profiles.members.length,2); profiles.members.forEach(p=>assert.deepEqual(Object.keys(p).sort(),['email','name','uid']));
    await denied(outsider,'getGroupMemberProfiles',{schemaVersion:1,groupId:group.groupId},'PERMISSION_DENIED');
    await denied(member,'updateGroup',{...id(),groupId:group.groupId,expectedRevision:'2',draft:{name:'Not owner'}},'PERMISSION_DENIED');
    await denied(owner,'addGroupMember',{...id(),groupId:group.groupId,expectedRevision:'2',email:member.email},'ALREADY_EXISTS');
  });
  await t.test('concurrent/stale revisions, omitted description and permanent error receipts', async () => {
    const one={...id(),groupId:group.groupId,expectedRevision:'2',draft:{name:'First'}};
    const two={...id(),groupId:group.groupId,expectedRevision:'2',draft:{name:'Second'}};
    const results=await Promise.all([call(owner,'updateGroup',one),call(owner,'updateGroup',two)]);
    assert.equal(results.filter(r=>r.value).length,1); assert.equal(results.filter(r=>r.error==='CONFLICT').length,1);
    group=results.find(r=>r.value).value.value; assert.equal(group.revision,'3'); assert.ok(!('description' in group));
    await denied(owner,'updateGroup',results[0].error ? one : two,'CONFLICT');
    await denied(owner,'deleteGroup',{...one},'VALIDATION');
    await denied(owner,'deleteGroup',{schemaVersion:1,requestId:one.requestId,groupId:group.groupId,expectedRevision:'3'},'IDEMPOTENCY_MISMATCH');
    assert.equal((await read(member,`groups/${group.groupId}`)).status,200);
    assert.equal((await read(owner,`groups/${group.groupId}`,{method:'DELETE'})).status,403);
  });
  await t.test('owner delete with durable acceptance, cleanup over multiple batches and replay after deletion', async () => {
    const parent=db.doc(`groups/${group.groupId}`); const batch=db.batch();
    for(let i=0;i<225;i++) batch.set(parent.collection('expenses').doc(`e${i}`),{schemaVersion:1,synthetic:true});
    for(let i=0;i<25;i++) batch.set(parent.collection('settlements').doc(`s${i}`),{schemaVersion:1,synthetic:true});
    await batch.commit();
    assert.equal((await read(member,`groups/${group.groupId}/expenses/e0`)).status,403);
    await denied(member,'deleteGroup',{...id(),groupId:group.groupId,expectedRevision:'3'},'PERMISSION_DENIED');
    const request={...id(),groupId:group.groupId,expectedRevision:'3'};
    const accepted=await ok(owner,'deleteGroup',request);
    assert.equal(accepted.value.status,'DELETING'); assert.equal(accepted.groupRevision,'4');
    const after=await call(owner,'updateGroup',{...id(),groupId:group.groupId,expectedRevision:'4',draft:{name:'Too late'}});
    assert.ok(['GROUP_DELETING','NOT_FOUND'].includes(after.error));
    assert.equal((await read(owner,`groups/${group.groupId}`)).status,403);
    await waitUntil(async()=> (await db.doc(`groupDeletionJobs/${group.groupId}`).get()).get('status')==='DELETED','deletion trigger completes');
    assert.equal((await parent.get()).exists,false);
    assert.equal((await parent.collection('expenses').get()).size,0); assert.equal((await parent.collection('settlements').get()).size,0);
    assert.deepEqual(await ok(owner,'deleteGroup',request),accepted);
    assert.equal(await cleanupBatch(db,group.groupId),true);
    assert.equal((await db.doc(`mutationReceipts/${owner.uid}/requests/${request.requestId}`).get()).get('outcome'),'SUCCESS');
    assert.equal((await read(owner,`groupDeletionJobs/${group.groupId}`)).status,403);
  });
  await t.test('Firebase login failure, X01 reset email/action and new-password login', async () => {
    assert.equal((await auth('signInWithPassword',{email:owner.email,password:'wrong',returnSecureToken:true})).status,400);
    assert.equal((await auth('signInWithPassword',{email:owner.email,password:'testing-password',returnSecureToken:true})).status,200);
    assert.equal((await auth('sendOobCode',{requestType:'PASSWORD_RESET',email:owner.email})).status,200);
    assert.equal((await auth('sendOobCode',{requestType:'PASSWORD_RESET',email:'invalid'})).status,400);
    const actions=await (await fetch('http://127.0.0.1:9099/emulator/v1/projects/demo-splitsmart/oobCodes')).json();
    const reset=actions.oobCodes.find(x=>x.email===owner.email && x.requestType==='PASSWORD_RESET'); assert.ok(reset);
    assert.equal((await auth('resetPassword',{oobCode:reset.oobCode,newPassword:'new-testing-password'})).status,200);
    assert.equal((await auth('signInWithPassword',{email:owner.email,password:'testing-password',returnSecureToken:true})).status,400);
    assert.equal((await auth('signInWithPassword',{email:owner.email,password:'new-testing-password',returnSecureToken:true})).status,200);
  });
});

test('canonical payload sorts keys recursively and distinguishes order/omission', () => {
  assert.equal(canonical({request:{b:1,a:'x'},operation:'x'}),'{"operation":"x","request":{"a":"x","b":1}}');
  assert.notEqual(canonical({a:[]}),canonical({})); assert.notEqual(canonical(['A','B']),canonical(['B','A']));
});

test('cleanup batch is bounded and rejects an ACTIVE parent before deleting children', async () => {
  const now=Timestamp.now(); let removed=0;
  const child={}; const ref={collection:()=>({orderBy:()=>({limit:()=>({query:true})})})};
  let active=false;
  const tx={get:async target=> target.query ? {docs:Array.from({length:200},()=>({ref:child}))} :
    {exists:true,get:key=>({status:active?'ACTIVE':'DELETING',schemaVersion:1,ownerId:'a',revision:5,updatedAt:now}[key])},
    delete:()=>removed++,update:()=>{}};
  const fake={doc:()=>ref,runTransaction:fn=>fn(tx)};
  assert.equal(await cleanupBatch(fake,'g'),false); assert.equal(removed,200);
  active=true; removed=0; await assert.rejects(()=>cleanupBatch(fake,'g')); assert.equal(removed,0);
});
