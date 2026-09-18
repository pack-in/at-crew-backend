// archify가 만든 HTML 뷰어에서 standalone SVG를 뽑아 파일로 쓴다.
//
// archify CLI에는 SVG를 내보내는 명령이 없고 뷰어의 Export 메뉴로만 된다. 그래서 headless Chrome을
// DevTools Protocol로 띄워 그 버튼을 누르고, 뷰어가 다운로드용으로 만드는 Blob을 가로챈다.
// --dump-dom은 뷰어의 애니메이션 루프 때문에 virtual time이 끝나지 않아 쓸 수 없다.
//
// 뷰어 내부 구조([data-format="svg"] 버튼, URL.createObjectURL 호출)에 기대므로 archify 버전을
// 올릴 때는 이 스크립트가 여전히 동작하는지 확인한다. 버전은 archify.lock.json에 고정돼 있다.
//
// 사용법: node archify_export.mjs <input.html> <output.svg>
// Node 22 이상이 필요하다(전역 WebSocket).
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const CHROME_CANDIDATES = [
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/Applications/Chromium.app/Contents/MacOS/Chromium',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
  '/usr/bin/chromium-browser',
];
const TIMEOUT_MS = 10_000;

function findChrome() {
  if (process.env.CHROME_BIN) return process.env.CHROME_BIN;
  const found = CHROME_CANDIDATES.find((c) => fs.existsSync(c));
  if (!found) throw new Error('Chrome을 찾지 못했다. CHROME_BIN 환경변수로 경로를 지정한다.');
  return found;
}

function withTimeout(promise, message) {
  return Promise.race([promise, new Promise((_, reject) => setTimeout(() => reject(new Error(message)), TIMEOUT_MS))]);
}

// 브라우저 레벨 DevTools 연결로 Browser.close를 보내 정상 종료를 요청하고, 1초 안에 끝나지 않으면
// 강제 종료한다. macOS headless Chrome은 Browser.close에 바로 응답하지만 프로세스는 몇 초씩 남는 것을
// 확인했다(SIGTERM도 마찬가지). 쓰고 버리는 프로필이라 강제 종료해도 잃을 것이 없다.
// 프로필 폴더는 종료를 확인한 뒤에 지운다.
async function shutdown(chrome, browserWsUrl) {
  if (chrome.exitCode !== null || chrome.signalCode !== null) return;
  const exited = new Promise((resolve) => chrome.once('exit', resolve));
  const timer = setTimeout(() => chrome.kill('SIGKILL'), 1000);
  if (browserWsUrl) {
    const browser = new WebSocket(browserWsUrl);
    browser.addEventListener('open', () => browser.send(JSON.stringify({ id: 1, method: 'Browser.close' })), { once: true });
    browser.addEventListener('error', () => chrome.kill('SIGKILL'), { once: true });
  } else {
    chrome.kill('SIGKILL');
  }
  await exited;
  clearTimeout(timer);
}

const debug = process.env.ARCHIFY_EXPORT_DEBUG ? (m) => console.error(`[${Date.now() - t0}ms] ${m}`) : () => {};
const t0 = Date.now();

async function main() {
  const [htmlPath, outPath] = process.argv.slice(2);
  if (!htmlPath || !outPath) throw new Error('사용법: node archify_export.mjs <input.html> <output.svg>');
  if (!fs.existsSync(htmlPath)) throw new Error(`입력 파일이 없다: ${htmlPath}`);

  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'archify-export-'));
  const chrome = spawn(findChrome(), ['--headless=new', '--disable-gpu', '--remote-debugging-port=0',
    `--user-data-dir=${profile}`, 'about:blank'], { stdio: ['ignore', 'ignore', 'pipe'] });
  let ws;
  let wsUrl;
  try {
    wsUrl = await withTimeout(new Promise((resolve) => {
      let buf = '';
      chrome.stderr.on('data', (chunk) => {
        buf += chunk;
        const m = buf.match(/ws:\/\/\S+/);
        if (m) resolve(m[0]);
      });
    }), 'Chrome DevTools 주소를 얻지 못했다');
    debug('chrome 기동');

    const port = new URL(wsUrl).port;
    const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
    ws = new WebSocket(targets.find((t) => t.type === 'page').webSocketDebuggerUrl);
    await withTimeout(new Promise((resolve) => ws.addEventListener('open', resolve, { once: true })), 'DevTools 연결 실패');
    debug('devtools 연결');

    let seq = 0;
    const pending = new Map();
    ws.addEventListener('message', (e) => {
      const msg = JSON.parse(e.data);
      if (msg.id && pending.has(msg.id)) {
        pending.get(msg.id)(msg);
        pending.delete(msg.id);
      }
    });
    const send = (method, params = {}) => new Promise((resolve) => {
      const id = ++seq;
      pending.set(id, resolve);
      ws.send(JSON.stringify({ id, method, params }));
    });

    await send('Page.enable');
    // 페이지 스크립트보다 먼저 심어야 뷰어가 만드는 SVG Blob을 놓치지 않는다.
    await send('Page.addScriptToEvaluateOnNewDocument', { source: `
      window.__archifySvg = new Promise((resolve) => {
        const original = URL.createObjectURL;
        URL.createObjectURL = function (blob) {
          if (blob && blob.type && blob.type.startsWith('image/svg+xml')) blob.text().then(resolve);
          return original.call(URL, blob);
        };
      });` });
    await send('Page.navigate', { url: pathToFileURL(path.resolve(htmlPath)).href });
    debug('navigate 요청');

    const res = await send('Runtime.evaluate', {
      awaitPromise: true,
      returnByValue: true,
      expression: `(async () => {
        await new Promise((r) => document.readyState === 'complete' ? r() : addEventListener('load', r, { once: true }));
        const button = document.querySelector('[data-format="svg"]');
        if (!button) throw new Error('뷰어에 SVG export 버튼([data-format="svg"])이 없다');
        button.click();
        return await Promise.race([
          window.__archifySvg,
          new Promise((_, reject) => setTimeout(() => reject(new Error('SVG export 시간 초과')), ${TIMEOUT_MS})),
        ]);
      })()`,
    });
    if (res.result.exceptionDetails) {
      throw new Error(res.result.exceptionDetails.exception?.description ?? JSON.stringify(res.result.exceptionDetails));
    }
    debug('export 완료');
    const svg = res.result.result.value;
    if (typeof svg !== 'string' || !svg.includes('<svg')) throw new Error('export 결과가 SVG가 아니다');
    if (svg.includes('<script')) throw new Error('export 결과에 <script>가 들어 있다');
    fs.writeFileSync(outPath, svg);
  } finally {
    ws?.close();
    await shutdown(chrome, wsUrl);
    debug('chrome 종료');
    fs.rmSync(profile, { recursive: true, force: true });
  }
}

// 열린 소켓·타이머가 남아 프로세스가 끝나지 않을 수 있어 명시적으로 종료한다.
main().then(() => process.exit(0), (err) => {
  console.error(err.message);
  process.exit(1);
});
