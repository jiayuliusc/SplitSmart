const {test}=require('node:test');const assert=require('node:assert/strict');
const v=require('../lib/infrastructure/validation');
const {balances}=require('../lib/financial/ledger');
const expense={title:'Meal',amountCents:'100',payerId:'a',participantIds:['a','b'],splitMethod:'EQUAL',category:'FOOD',splitInputs:[]};
const invalid=data=>assert.throws(()=>v.expenseDraft(data),error=>error.code==='VALIDATION');
test('expense drafts reject duplicate participants and unused or duplicate split inputs',()=>{
 invalid({...expense,participantIds:['a','a','b']});
 invalid({...expense,splitInputs:[{participantId:'a',amountCents:'0'}]});
 invalid({...expense,splitMethod:'EXACT',splitInputs:[{participantId:'a',amountCents:'0'},{participantId:'a',amountCents:'100'}]});
 invalid({...expense,splitMethod:'EXACT',splitInputs:[{participantId:'a',amountCents:'0',basisPoints:0},{participantId:'b',amountCents:'100'}]});
 invalid({...expense,splitMethod:'PERCENTAGE',splitInputs:[{participantId:'a',basisPoints:0},{participantId:'b',amountCents:'100',basisPoints:10000}]});
});
test('bounds apply to final pair balances and separate pay/receive aggregates',()=>{
 const max=9007199254740991n;
 const e=(payer,person,amount)=>({creatorId:payer,payerId:payer,shares:[{participantId:person,amountCents:String(amount)}]});
 const ledger={memberIds:['a','b','c'],expenses:[e('a','b',max),e('a','c',1n)],settlements:[]};
 assert.throws(()=>balances(ledger),err=>err.code==='OUT_OF_RANGE');
 ledger.settlements=[{creatorId:'b',payerId:'b',payeeId:'a',amountCents:'1'}];
 assert.equal(balances(ledger).get('a').get('b'),max-1n);
 ledger.expenses=[e('a','b',max),e('a','b',1n)];ledger.settlements=[];
 assert.throws(()=>balances(ledger),err=>err.code==='OUT_OF_RANGE');
});
test('replacement debt uses unbounded intermediate ledger before final bounds',()=>{
 const max=9007199254740991n;
 const ledger={memberIds:['a','b'],expenses:[{creatorId:'a',payerId:'a',shares:[{participantId:'b',amountCents:String(max)}]},{creatorId:'a',payerId:'a',shares:[{participantId:'b',amountCents:'1'}]}],settlements:[]};
 assert.equal(balances(ledger,false).get('a').get('b'),max+1n);
});
const {computeShares}=require('../lib/expenses/shares');
const {expenseRecord}=require('../lib/financial/ledger');
const {Timestamp}=require('firebase-admin/firestore');
test('percentage allocation ranks exact fractional remainders before UID, ties by UID',()=>{
 const inputs=[{participantId:'a',basisPoints:1000},{participantId:'b',basisPoints:9000}];
 assert.deepEqual(computeShares('1',['b','a'],'PERCENTAGE',inputs),[
  {participantId:'a',amountCents:'0',basisPoints:1000},{participantId:'b',amountCents:'1',basisPoints:9000}]);
 assert.deepEqual(computeShares('2',['c','b','a'],'PERCENTAGE',[{participantId:'a',basisPoints:1000},{participantId:'b',basisPoints:4500},{participantId:'c',basisPoints:4500}]),[
  {participantId:'a',amountCents:'0',basisPoints:1000},{participantId:'b',amountCents:'1',basisPoints:4500},{participantId:'c',amountCents:'1',basisPoints:4500}]);
 assert.deepEqual(computeShares('1',['b','a'],'PERCENTAGE',[{participantId:'a',basisPoints:5000},{participantId:'b',basisPoints:5000}]),[
  {participantId:'a',amountCents:'1',basisPoints:5000},{participantId:'b',amountCents:'0',basisPoints:5000}]);
 const large=computeShares('9007199254740991',['a','b'],'PERCENTAGE',inputs);
 assert.deepEqual(large.map(s=>s.amountCents),['900719925474099','8106479329266892']);
 assert.equal(large.reduce((sum,s)=>sum+BigInt(s.amountCents),0n),9007199254740991n);
});
test('stored percentage allocations must match contract and never silently repair historical shares',()=>{
 const now=Timestamp.now();
 const data={schemaVersion:1,title:'Percentage',amountCents:1,currency:'USD',creatorId:'a',payerId:'a',participantIds:['a','b'],splitMethod:'PERCENTAGE',category:'OTHER',localDate:'2026-11-04',createdAt:now,updatedAt:now,shares:[{participantId:'a',amountCents:0,basisPoints:1000},{participantId:'b',amountCents:1,basisPoints:9000}]};
 assert.deepEqual(expenseRecord('e','g',data).shares.map(s=>s.amountCents),['0','1']);
 assert.throws(()=>expenseRecord('e','g',{...data,shares:[{participantId:'a',amountCents:1,basisPoints:1000},{participantId:'b',amountCents:0,basisPoints:9000}]}),e=>e.code==='INTERNAL');
});
test('settlement drafts accept an optional payer and validate its format',()=>{
 const debtor=v.settlementDraft({payeeId:'a',amountCents:'100'});
 assert.equal(debtor.payeeId,'a');assert.equal('payerId' in debtor,false);
 const recipient=v.settlementDraft({payerId:'b',payeeId:'a',amountCents:'100'});
 assert.equal(recipient.payerId,'b');assert.equal(recipient.payeeId,'a');
 for(const payerId of ['', 'bad id!', null, 42]) {
  assert.throws(()=>v.settlementDraft({payerId,payeeId:'a',amountCents:'100'}),String(payerId));
 }
});
