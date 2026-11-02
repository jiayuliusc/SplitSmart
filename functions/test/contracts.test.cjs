const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');
const v1 = require('../lib/contracts/v1.js');
const fixture = require('../../contracts/v1/fixtures.json');

function target(type) {
  const [name, kind] = type.split('.');
  if (kind === 'request') return `V.Requests[${JSON.stringify(name)}]`;
  if (kind === 'response') return `V.Responses[${JSON.stringify(name)}]`;
  return `V.PayloadTypes[${JSON.stringify(name)}]`;
}

// Test-only representation checks; these are NOT a runtime callable decoder.
function checkEncoding(value) {
  assert.notEqual(value, null, 'null must be omitted, never serialized');
  if (Array.isArray(value)) return value.forEach(checkEncoding);
  if (typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    assert.notEqual(child, null, key);
    if (key.endsWith('Cents') || ['revision', 'expectedRevision', 'groupRevision'].includes(key)) {
      assert.equal(typeof child, 'string', key);
      assert.match(child, /^(0|[1-9][0-9]*)$/, key);
      assert.ok(BigInt(child) <= v1.MAX_ABSOLUTE_CENTS, key);
    }
    if (key === 'basisPoints') assert.ok(Number.isInteger(child) && child >= 0 && child <= 10000);
    if (key === 'schemaVersion') assert.equal(child, 1);
    if (key === 'currency') assert.equal(child, 'USD');
    if (key === 'localDate') {
      assert.match(child, /^\d{4}-\d{2}-\d{2}$/);
      assert.ok(Number(child.slice(0, 4)) >= 1);
      assert.equal(new Date(`${child}T00:00:00Z`).toISOString().slice(0, 10), child);
    }
    if (key === 'requestId') assert.match(child, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    checkEncoding(child);
  }
  if ('seconds' in value) {
    assert.ok(Number.isInteger(value.seconds));
    assert.ok(value.seconds >= -62135596800 && value.seconds <= 253402300799);
    assert.ok(Number.isInteger(value.nanoseconds));
    assert.ok(value.nanoseconds >= 0 && value.nanoseconds < 1000000000);
    assert.equal(value.nanoseconds % 1000, 0, 'Firestore timestamp precision is microseconds');
  }
}

test('common JSON satisfies the actual TypeScript request, response and enum types', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'splitsmart-contracts-'));
  try {
    const source = path.join(dir, 'compatibility.ts');
    let code = `import type * as V from ${JSON.stringify(path.resolve(__dirname, '../src/contracts/v1'))};\n`;
    code += 'type Assert<T extends true> = T;\n';
    for (const [shape, field] of [['Group', 'memberIds'], ['Expense', 'participantIds'],
      ['ExpenseDraft', 'participantIds'], ['LedgerSnapshot', 'memberIds']]) {
      code += `type Primitive${shape}Ids = Assert<V.${shape}["${field}"][number] extends string ? true : false>;\n`;
    }
    for (const [name, values] of Object.entries(fixture.enums)) {
      code += `const enum${name} = ${JSON.stringify(values)} as const satisfies readonly V.${name}[];\n`;
      code += `type All${name} = Assert<Exclude<V.${name}, typeof enum${name}[number]> extends never ? true : false>;\n`;
    }
    fixture.cases.forEach((c, i) => {
      code += `const valid${i}: ${target(c.type)} = ${JSON.stringify(c.value)};\n`;
    });
    fixture.invalidCases.filter(c => c.typeError).forEach((c, i) => {
      code += `// @ts-expect-error ${c.id}\nconst invalid${i}: ${target(c.type)} = ${JSON.stringify(c.value)};\n`;
    });
    fs.writeFileSync(source, code);
    const program = ts.createProgram([source], {
      strict: true, exactOptionalPropertyTypes: true, noEmit: true,
      skipLibCheck: true, target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.CommonJS, types: []
    });
    const diagnostics = ts.getPreEmitDiagnostics(program);
    assert.equal(diagnostics.length, 0, ts.formatDiagnosticsWithColorAndContext(diagnostics, {
      getCanonicalFileName: f => f, getCurrentDirectory: () => process.cwd(), getNewLine: () => '\n'
    }));
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('common numeric, optional and timestamp encodings remain exact', () => {
  fixture.cases.forEach(c => checkEncoding(c.value));
  for (const c of fixture.invalidCases.filter(c => !c.typeError)) {
    assert.throws(() => checkEncoding(c.value), c.id);
  }
  assert.equal(v1.MAX_ABSOLUTE_CENTS.toString(), fixture.constants.maxAbsoluteCents);
  assert.equal(v1.MAX_REVISION.toString(), fixture.constants.maxRevision);
  assert.equal(v1.TOTAL_BASIS_POINTS, fixture.constants.totalBasisPoints);
  assert.equal(v1.CURRENCY, fixture.constants.currency);
  assert.equal(BigInt(Number(v1.MAX_ABSOLUTE_CENTS)), v1.MAX_ABSOLUTE_CENTS);
  const checkSigned = value => {
    assert.match(value, /^(0|-?[1-9][0-9]*)$/);
    const amount = BigInt(value);
    assert.ok(amount >= -v1.MAX_ABSOLUTE_CENTS && amount <= v1.MAX_ABSOLUTE_CENTS);
    assert.equal(BigInt(Number(amount)), amount);
  };
  fixture.signedCentsExamples.forEach(checkSigned);
  fixture.invalidSignedCentsExamples.forEach(value => assert.throws(() => checkSigned(value)));
});

test('every callable has both fixtures and ownership; the entry point exports no handlers', () => {
  assert.deepEqual(v1.CALLABLE_OWNERS, fixture.owners);
  for (const name of Object.keys(v1.CALLABLE_OWNERS)) {
    assert.ok(fixture.cases.some(c => c.type === `${name}.request`));
    assert.ok(fixture.cases.some(c => c.type === `${name}.response`));
  }
  assert.deepEqual(Object.keys(require('../lib/index.js')), []);
});
