(function() {
    'use strict';

    const API = '/api';
    let simAbortCtrl, listAbortCtrl, singleAbortCtrl;

    function $(id) { return document.getElementById(id); }
    function qs(sel, el = document) { return el.querySelector(sel); }
    function qsa(sel, el = document) { return el.querySelectorAll(sel); }

    // Tab switching
    qsa('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            qsa('.tab-btn').forEach(b => b.classList.remove('active'));
            qsa('.tab-content').forEach(c => c.classList.remove('active'));
            btn.classList.add('active');
            $(btn.dataset.tab).classList.add('active');
        });
    });

    // API helpers
    async function api(endpoint, body = null) {
        const opts = { method: body ? 'POST' : 'GET', headers: { 'Content-Type': 'application/json' } };
        if (body) opts.body = JSON.stringify(body);
        const r = await fetch(API + endpoint, opts);
        const data = await r.json().catch(() => ({}));
        if (!r.ok) throw new Error(data.error || r.statusText);
        return data;
    }

    function showAlert(msg) { alert(msg); }

    // ========== Simulation Tab ==========
    const simFailuresTbody = $('simFailures').querySelector('tbody');
    let simSelectedFailure = null;

    $('simRun').addEventListener('click', async () => {
        simAbortCtrl = new AbortController();
        $('simRun').disabled = true;
        $('simStop').disabled = false;
        $('simStats').textContent = 'Running...';
        $('simProgress').value = 0;
        $('simReport').value = '';
        simFailuresTbody.innerHTML = '';
        $('simDetails').innerHTML = '<div class="muted">Select a failed game to see details.</div>';

        try {
            const data = await api('/simulate/random', {
                testTimes: parseInt($('simTestTimes').value) || 1000,
                topK: parseInt($('simTopK').value) || 20,
                maxAttempts: parseInt($('simMaxAttempts').value) || 6,
                wordLength: parseInt($('simWordLength').value) || 5,
                threads: parseInt($('simThreads').value) || 8,
                hardMode: $('simHardMode').checked,
                answersPath: $('simAnswersPath').value.trim() || null,
                charFrequencyWeight: parseInt($('simCharWeight').value) || 1,
                positionTopBonus: parseInt($('simPosBonus').value) || 100,
                repeatPenaltyBase: parseInt($('simRepeatBase').value) || 25
            });
            $('simProgress').value = 100;
            const wr = data.completed ? (data.wins * 100 / data.completed) : 0;
            $('simStats').textContent = `Done. Time=${data.totalSeconds.toFixed(2)}s, Completed=${data.completed}, Wins=${data.wins}, WinRate=${wr.toFixed(2)}%, AvgAttempts=${data.avgAttempts.toFixed(3)}, Failures=${data.failures.length}`;
            $('simReport').value = buildReport(data);

            data.failures.forEach(fr => {
                const tr = document.createElement('tr');
                tr.dataset.index = fr.index;
                tr.innerHTML = `<td>${fr.index}</td><td>${esc(fr.answer)}</td><td>${fr.attempts}</td><td>${esc(fr.lastGuess || '')}</td><td>${esc(fr.tag || '')}</td>`;
                tr.addEventListener('click', () => {
                    qsa('#simFailures tbody tr').forEach(r => r.classList.remove('selected'));
                    tr.classList.add('selected');
                    renderFailureDetails(fr, $('simDetails'));
                });
                simFailuresTbody.appendChild(tr);
            });
        } catch (e) {
            $('simStats').textContent = 'Failed: ' + e.message;
            showAlert(e.message);
        } finally {
            $('simRun').disabled = false;
            $('simStop').disabled = true;
        }
    });

    $('simStop').addEventListener('click', () => {
        if (simAbortCtrl) simAbortCtrl.abort();
    });

    $('simCopyDetails').addEventListener('click', () => {
        const el = $('simDetails');
        if (el && el.textContent) navigator.clipboard.writeText(el.innerText);
    });

    // ========== List Simulation Tab ==========
    const listGamesTbody = $('listGames').querySelector('tbody');

    $('listRun').addEventListener('click', async () => {
        $('listRun').disabled = true;
        $('listStop').disabled = false;
        $('listStats').textContent = 'Running...';
        $('listProgress').value = 0;
        $('listReport').value = '';
        listGamesTbody.innerHTML = '';
        $('listDetails').innerHTML = '<div class="muted">Select a game to see details.</div>';

        try {
            const data = await api('/simulate/list', {
                topK: parseInt($('listTopK').value) || 20,
                maxAttempts: parseInt($('listMaxAttempts').value) || 6,
                wordLength: parseInt($('listWordLength').value) || 5,
                threads: parseInt($('listThreads').value) || 8,
                hardMode: $('listHardMode').checked,
                answersPath: $('listAnswersPath').value.trim() || null,
                charFrequencyWeight: parseInt($('listCharWeight').value) || 1,
                positionTopBonus: parseInt($('listPosBonus').value) || 100,
                repeatPenaltyBase: parseInt($('listRepeatBase').value) || 25
            });
            $('listProgress').value = 100;
            const wr = data.completed ? (data.wins * 100 / data.completed) : 0;
            const fails = data.completed - data.wins;
            $('listStats').textContent = `Done. Time=${data.totalSeconds.toFixed(2)}s, Completed=${data.completed}, Wins=${data.wins}, WinRate=${wr.toFixed(2)}%, AvgAttempts=${data.avgAttempts.toFixed(3)}, Failures=${fails}`;
            $('listReport').value = buildReport(data);

            data.allGames.forEach(gr => {
                const tr = document.createElement('tr');
                tr.dataset.index = gr.index;
                const sr = gr.toSimResult ? gr : { index: gr.index, answer: gr.answer, win: gr.win, attempts: gr.attempts, steps: gr.steps };
                tr.innerHTML = `<td>${gr.index}</td><td>${esc(gr.answer)}</td><td>${gr.win ? 'WIN' : 'FAIL'}</td><td>${gr.attempts}</td><td>${esc(gr.lastGuess || '')}</td><td>${esc(gr.tag || '')}</td>`;
                tr.addEventListener('click', () => {
                    qsa('#listGames tbody tr').forEach(r => r.classList.remove('selected'));
                    tr.classList.add('selected');
                    renderFailureDetails({ ...sr, steps: gr.steps }, $('listDetails'));
                });
                listGamesTbody.appendChild(tr);
            });
        } catch (e) {
            $('listStats').textContent = 'Failed: ' + e.message;
            showAlert(e.message);
        } finally {
            $('listRun').disabled = false;
            $('listStop').disabled = true;
        }
    });

    $('listCopyDetails').addEventListener('click', () => {
        const el = $('listDetails');
        if (el && el.textContent) navigator.clipboard.writeText(el.innerText);
    });

    // ========== Single Word Tab ==========
    $('singleLoadWords').addEventListener('click', async () => {
        try {
            const len = parseInt($('singleWordLength').value) || 5;
            const path = $('singleAnswersPath').value.trim() || null;
            const data = await api(`/answers?length=${len}` + (path ? '&path=' + encodeURIComponent(path) : ''));
            const listEl = $('singleWordList');
            listEl.innerHTML = '';
            (data.words || []).forEach(w => {
                const div = document.createElement('div');
                div.className = 'word-item';
                div.textContent = w;
                div.addEventListener('dblclick', () => {
                    $('singleSelectedWord').value = w;
                    $('singleRun').click();
                });
                listEl.appendChild(div);
            });
            $('singleStats').textContent = 'Words loaded: ' + (data.words || []).length;
        } catch (e) {
            $('singleStats').textContent = 'Failed to load words.';
            showAlert(e.message);
        }
    });

    $('singleRun').addEventListener('click', async () => {
        const word = $('singleSelectedWord').value.trim().toUpperCase();
        if (!word) { $('singleStats').textContent = 'Please select a word.'; return; }
        const len = parseInt($('singleWordLength').value) || 5;
        if (word.length !== len || !/^[A-Z]+$/.test(word)) {
            $('singleStats').textContent = 'Selected word is invalid.';
            return;
        }

        $('singleRun').disabled = true;
        $('singleStop').disabled = false;
        $('singleStats').textContent = 'Running...';
        $('singleReport').value = '';
        $('singleDetails').innerHTML = '<div class="muted">Running...</div>';

        try {
            const data = await api('/simulate/single', {
                selectedWord: word,
                topK: parseInt($('singleTopK').value) || 20,
                maxAttempts: parseInt($('singleMaxAttempts').value) || 6,
                wordLength: len,
                hardMode: $('singleHardMode').checked,
                answersPath: $('singleAnswersPath').value.trim() || null,
                charFrequencyWeight: parseInt($('singleCharWeight').value) || 1,
                positionTopBonus: parseInt($('singlePosBonus').value) || 100,
                repeatPenaltyBase: parseInt($('singleRepeatBase').value) || 25
            });
            const r = data.result;
            $('singleStats').textContent = `Done. ${r.win ? 'WIN' : 'FAIL'}, Attempts=${r.attempts}, Time=${data.totalSeconds.toFixed(2)}s`;
            $('singleReport').value = buildReport({ ...data, completed: 1, wins: r.win ? 1 : 0, avgAttempts: r.attempts });
            renderFailureDetails(r, $('singleDetails'));
        } catch (e) {
            $('singleStats').textContent = 'Failed: ' + e.message;
            showAlert(e.message);
        } finally {
            $('singleRun').disabled = false;
            $('singleStop').disabled = true;
        }
    });

    $('singleCopyDetails').addEventListener('click', () => {
        const el = $('singleDetails');
        if (el && el.textContent) navigator.clipboard.writeText(el.innerText);
    });

    // Load words on init
    $('singleLoadWords').click();

    // ========== Manual Guess Tab ==========
    const MAX_ATTEMPTS = 6;
    const WORD_LENGTH = 5;
    let manualAttempt = 0;
    let manualGuesses = [];

    const manualAttemptsEl = $('manualAttempts');
    const manualCandidatesTbody = $('manualCandidates').querySelector('tbody');
    const manualProbesTbody = $('manualProbes').querySelector('tbody');

    for (let i = 0; i < MAX_ATTEMPTS; i++) {
        const row = document.createElement('div');
        row.className = 'attempt-row' + (i > 0 ? ' disabled' : '');
        row.dataset.index = i;
        row.innerHTML = `
            <label>第 ${i + 1} 次</label>
            <input type="text" maxlength="${WORD_LENGTH}" placeholder="五个字母" class="manual-guess">
            ${Array(WORD_LENGTH).fill(0).map((_, j) => `
                <select class="color-select" data-pos="${j}">
                    <option value="B">B(灰)</option>
                    <option value="Y">Y(黄)</option>
                    <option value="G">G(绿)</option>
                </select>
            `).join('')}
        `;
        manualAttemptsEl.appendChild(row);

        row.querySelector('.manual-guess').addEventListener('input', ev => {
            ev.target.value = ev.target.value.toUpperCase().replace(/[^A-Z]/g, '');
        });

        row.querySelectorAll('.color-select').forEach(sel => {
            sel.addEventListener('change', () => {
                const pos = parseInt(sel.dataset.pos);
                const val = sel.value;
                for (let r = i + 1; r < MAX_ATTEMPTS; r++) {
                    const below = manualAttemptsEl.querySelector(`[data-index="${r}"] .color-select[data-pos="${pos}"]`);
                    if (below) {
                        if (val === 'G') below.value = 'G';
                        else if (below.value === 'G') below.value = 'B';
                    }
                }
            });
        });
    }

    function buildGuessResult(rowEl) {
        const guess = rowEl.querySelector('.manual-guess').value.trim().toUpperCase();
        let result = '';
        rowEl.querySelectorAll('.color-select').forEach((sel, i) => {
            const code = sel.value.charAt(0).toLowerCase();
            result += code + guess.charAt(i);
        });
        return result;
    }

    function manualRefresh() {
        const path = $('manualAnswersPath').value.trim() || null;
        const topK = parseInt($('manualTopK').value) || 200;
        const hardMode = $('manualHardMode').checked;

        api('/manual/refresh', {
            answersPath: path || null,
            topK,
            hardMode,
            maxAttempts: MAX_ATTEMPTS,
            wordLength: WORD_LENGTH,
            guesses: manualGuesses,
            charFrequencyWeight: 1,
            positionTopBonus: 100,
            repeatPenaltyBase: 25
        }).then(data => {
            manualCandidatesTbody.innerHTML = '';
            (data.candidates || []).forEach(c => {
                const tr = document.createElement('tr');
                tr.innerHTML = `<td>${esc(c.word)}</td><td>${c.score}</td>`;
                tr.addEventListener('dblclick', () => {
                    const row = manualAttemptsEl.querySelector(`[data-index="${manualAttempt}"]`);
                    if (row && !row.classList.contains('disabled')) row.querySelector('.manual-guess').value = c.word;
                });
                manualCandidatesTbody.appendChild(tr);
            });

            manualProbesTbody.innerHTML = '';
            (data.probes || []).forEach(p => {
                const tr = document.createElement('tr');
                tr.innerHTML = `<td>${esc(p.word)}</td><td>${esc(p.newLetters || '')}</td><td>${esc(p.source || '')}</td>`;
                tr.addEventListener('dblclick', () => {
                    const row = manualAttemptsEl.querySelector(`[data-index="${manualAttempt}"]`);
                    if (row && !row.classList.contains('disabled')) row.querySelector('.manual-guess').value = p.word;
                });
                manualProbesTbody.appendChild(tr);
            });

            $('manualCandidatesCount').textContent = `候选: ${data.candidatesCount || 0} / 显示: ${(data.candidates || []).length}`;
            $('manualProbesCount').textContent = `探测词: ${data.probesCount || 0}`;
            const ng = data.nextGuess || '';
            $('manualStatus').textContent = ng ? `建议猜测: ${ng} [${data.strategy || ''}]` : '当前无可用建议。';
        }).catch(e => showAlert(e.message));
    }

    function manualReset() {
        manualAttempt = 0;
        manualGuesses = [];
        manualAttemptsEl.querySelectorAll('.attempt-row').forEach((row, i) => {
            row.classList.toggle('disabled', i > 0);
            row.querySelector('.manual-guess').value = '';
            row.querySelectorAll('.color-select').forEach(s => s.value = 'B');
        });
        $('manualConfirm').disabled = false;
        $('manualStatus').textContent = '请输入猜测并选择颜色，然后确认。';
        manualRefresh();
    }

    $('manualRefresh').addEventListener('click', manualRefresh);
    $('manualReset').addEventListener('click', manualReset);

    $('manualConfirm').addEventListener('click', () => {
        if (manualAttempt >= MAX_ATTEMPTS) { $('manualConfirm').disabled = true; return; }
        const row = manualAttemptsEl.querySelector(`[data-index="${manualAttempt}"]`);
        const guess = row.querySelector('.manual-guess').value.trim().toUpperCase();
        if (guess.length !== WORD_LENGTH || !/^[A-Z]+$/.test(guess)) {
            showAlert('请输入 5 个字母的单词。');
            return;
        }
        const result = buildGuessResult(row);

        manualGuesses.push({ word: guess, result });
        row.classList.add('disabled');
        manualAttempt++;
        if (manualAttempt < MAX_ATTEMPTS) {
            manualAttemptsEl.querySelector(`[data-index="${manualAttempt}"]`).classList.remove('disabled');
            $('manualStatus').textContent = `已确认第 ${manualAttempt} 次，继续下一次。`;
        } else {
            $('manualStatus').textContent = '已完成 6 次猜测。';
            $('manualConfirm').disabled = true;
        }
        manualRefresh();
    });

    manualRefresh();

    // ========== Shared: render failure details ==========
    function esc(s) {
        if (s == null) return '';
        const d = document.createElement('div');
        d.textContent = s;
        return d.innerHTML;
    }

    function buildReport(data) {
        const wr = data.completed ? (data.wins * 100 / data.completed) : 0;
        let s = `Report\nCompleted: ${data.completed}\nWins: ${data.wins}\nFailures: ${data.completed - data.wins}\nWinRate: ${wr.toFixed(2)}%\nAvgAttempts: ${data.avgAttempts?.toFixed(3) || 0}\nTime: ${data.totalSeconds?.toFixed(2) || 0}s\nThreads: ${data.threads || 1}\nHardMode: ${data.hardMode || false}\n`;
        if (data.scoreParams) {
            s += `\nScore Params\ncharFrequencyWeight: ${data.scoreParams.charFrequencyWeight}\npositionTopBonus: ${data.scoreParams.positionTopBonus}\nrepeatPenaltyBase: ${data.scoreParams.repeatPenaltyBase}\n`;
        }
        return s;
    }

    function buildResultHtml(result) {
        if (!result || result === 'GAME OVER' || result === 'NO CANDIDATES')
            return `<span class="v">${esc(result || '')}</span>`;
        const cells = [];
        let color = null;
        for (let i = 0; i < result.length; i++) {
            const c = result[i];
            if (c === 'g' || c === 'y' || c === 'b') { color = c; continue; }
            if (/[A-Z]/.test(c) && color)
                cells.push(`<span class="res res-${color}">${esc(c)}</span>`);
        }
        return cells.join('') || `<span class="v">${esc(result)}</span>`;
    }

    function buildFilterPathHtml(pathLine) {
        const parts = pathLine.split(' | ');
        let coverage = '', rankedPool = '', regex = '', regexMatched = '', accepted = '', excluded = '', rules = '', sample = '';
        parts.forEach(p => {
            if (p.startsWith('coverage>=')) coverage = p;
            else if (p.startsWith('rankedPool=')) rankedPool = p;
            else if (p.startsWith('regex=')) regex = p.slice(6);
            else if (p.startsWith('regexMatched=')) regexMatched = p;
            else if (p.startsWith('accepted=')) accepted = p;
            else if (p.startsWith('excluded{')) excluded = p;
            else if (p.startsWith('rules=')) rules = p;
            else if (p.startsWith('sample{')) sample = p;
        });
        let html = `<div class="path-card"><div><span class="cov">${esc(coverage)}</span> `;
        if (rankedPool) html += `<span class="chip">${esc(rankedPool)}</span>`;
        if (regexMatched) html += `<span class="chip">${esc(regexMatched)}</span>`;
        if (accepted) html += `<span class="chip">${esc(accepted)}</span>`;
        if (excluded) html += `<span class="chip">${esc(excluded)}</span>`;
        html += '</div>';
        if (regex) html += `<div class="regex">${esc(regex)}</div>`;
        if (rules) html += `<div class="muted">${esc(rules)}</div>`;
        if (sample) html += `<div class="muted">${esc(sample)}</div>`;
        html += '</div>';
        return html;
    }

    function buildMatchedWordsHtml(words, maxD) {
        if (!words || !words.length) return '[]';
        const end = Math.min(maxD || 12, words.length);
        return words.slice(0, end).map(entry => {
            let w = entry, extra = '';
            const idx = entry.indexOf('(');
            if (idx > 0) { w = entry.slice(0, idx); extra = entry.slice(idx); }
            return `<span class="chip"><span class="word">${esc(w)}</span> ${esc(extra)}</span>`;
        }).join('') + (words.length > end ? `<span class="chip">... +${words.length - end}</span>` : '');
    }

    function renderFailureDetails(fr, container) {
        if (!container || !fr) return;
        const steps = fr.steps || [];
        let html = `
            <div class="title">Game #${fr.index}</div>
            <div class="meta">Answer: <span class="v">${esc(fr.answer)}</span></div>
            <div class="meta">Attempts: ${fr.attempts}</div>
            <div class="title">Game Board</div>
        `;
        steps.forEach((step, i) => {
            html += `<div><span class="k">#${i + 1}</span> <span class="word">${esc(step.guess)}</span> ${buildResultHtml(step.result)}</div>`;
        });

        steps.forEach((step, i) => {
            html += `<div class="title">Step ${i + 1}</div>
                <div><span class="k">Guess:</span> <span class="v">${esc(step.guess)}</span></div>
                <div><span class="k">Result:</span> ${buildResultHtml(step.result)}</div>`;
            if (step.strategy) html += `<div><span class="k">Strategy:</span> ${esc(step.strategy)}</div>`;
            if (step.strategyReason) html += `<div><span class="k">StrategyReason:</span> ${esc(step.strategyReason)}</div>`;
            if (step.filterRounds > 0 || step.checkedWords > 0)
                html += `<div><span class="muted">FilterRounds:</span> ${step.filterRounds}, <span class="muted">CheckedWords:</span> ${step.checkedWords}</div>`;
            if (step.targetCoverage > 0) html += `<div><span class="muted">TargetCoverage:</span> ${step.targetCoverage}</div>`;
            if (step.matchedWords && step.matchedWords.length)
                html += `<div><span class="k">MatchedWords:</span> ${buildMatchedWordsHtml(step.matchedWords)}</div>`;
            if (step.filterPath && step.filterPath.length) {
                html += `<div><span class="k">FilterPath(n->2):</span></div>`;
                step.filterPath.forEach(pl => { html += buildFilterPathHtml(pl); });
            }
            html += `<div><span class="k">Top Candidates:</span></div>`;
            (step.topCandidates || []).forEach((cs, j) => {
                html += `<div class="cand">${String(j + 1).padStart(2)}) <span class="word">${esc(cs.word)}</span>  ${cs.score}</div>`;
            });
        });
        container.innerHTML = html;
    }
})();
