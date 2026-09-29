const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const banner = "try { const _nc = require('crypto'); if (!globalThis.crypto || !globalThis.crypto.subtle) { globalThis.crypto = _nc.webcrypto || _nc; } } catch (_) {}";

console.log('Building bundle.cjs via esbuild...');
const cmd = `npx.cmd esbuild index.js --bundle --platform=node --target=node18 --format=cjs --banner:js="${banner}" --outfile=bundle.cjs`;
const output = execSync(cmd, { cwd: __dirname }).toString();
if (output) console.log(output);

// Also copy to dist/bundle.cjs
const distDir = path.join(__dirname, 'dist');
if (!fs.existsSync(distDir)) {
    fs.mkdirSync(distDir, { recursive: true });
}
fs.copyFileSync(path.join(__dirname, 'bundle.cjs'), path.join(distDir, 'bundle.cjs'));

console.log('Successfully built bundle.cjs and updated dist/bundle.cjs!');
