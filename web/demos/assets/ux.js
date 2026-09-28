/**
 * 演示页跨页交互层：toast、删除撤销、底部批量条、筛选恢复、内联错误重试、快捷键
 * 仅静态演示用，不参与构建；页面通过 data-ux-* 声明式接入
 */
(function () {
  var store = {
    get: function (key) {
      try { return window.sessionStorage.getItem(key); } catch (e) { return null; }
    },
    set: function (key, val) {
      try { window.sessionStorage.setItem(key, val); } catch (e) { /* 隐私模式下静默降级 */ }
    },
    remove: function (key) {
      try { window.sessionStorage.removeItem(key); } catch (e) { /* 同上 */ }
    },
  };

  /* ---------------- Toast ---------------- */
  var toastRoot;
  function ensureRoot() {
    if (!toastRoot) {
      toastRoot = document.createElement('div');
      toastRoot.className = 'ux-toasts';
      document.body.appendChild(toastRoot);
    }
    return toastRoot;
  }

  function toast(msg, opts) {
    opts = opts || {};
    var duration = opts.duration == null ? 3000 : opts.duration;
    var el = document.createElement('div');
    el.className = 'ux-toast' + (opts.type ? ' ux-toast--' + opts.type : '');

    var dot = document.createElement('span');
    dot.className = 'ux-toast__dot';
    el.appendChild(dot);

    var body = document.createElement('span');
    body.className = 'ux-toast__msg';
    body.textContent = msg;
    el.appendChild(body);

    var action = opts.actionLabel && document.createElement('button');
    if (action) {
      action.type = 'button';
      action.className = 'ux-toast__action';
      action.textContent = opts.actionLabel;
      action.addEventListener('click', function () {
        if (opts.onAction) opts.onAction();
        close(true);
      });
      el.appendChild(action);
    }

    var counter;
    if (duration >= 1000 && (action || opts.showCountdown)) {
      counter = document.createElement('span');
      counter.className = 'ux-toast__count';
      el.appendChild(counter);
    }

    ensureRoot().appendChild(el);

    var remaining = duration;
    var startedAt = Date.now();
    var timer = window.setTimeout(function () { close(); }, remaining);
    var tick;
    if (counter) {
      tick = window.setInterval(function () {
        var left = Math.max(0, remaining - (Date.now() - startedAt));
        counter.textContent = Math.ceil(left / 1000) + 's';
      }, 200);
      counter.textContent = Math.ceil(remaining / 1000) + 's';
    }

    function close(immediate) {
      window.clearTimeout(timer);
      window.clearInterval(tick);
      if (immediate) { el.remove(); return; }
      el.classList.add('is-leaving');
      window.setTimeout(function () { el.remove(); }, 180);
    }
    return { close: close };
  }

  // 页面动态插入节点后可能重复 bind，统一按元素去重
  function once(el, binder) {
    if (el.__uxBound) return;
    el.__uxBound = true;
    binder(el);
  }

  /* ---------------- 删除 + 撤销 ---------------- */
  function bindUndo(el) {
    el.addEventListener('click', function () {
      var rowSel = el.getAttribute('data-ux-undo');
      var row = rowSel ? el.closest(rowSel) : el.closest('tr');
      if (!row) return;
      var seconds = parseInt(el.getAttribute('data-ux-undo-seconds') || '5', 10);
      var name = el.getAttribute('data-ux-undo-name');
      if (!name) {
        var named = row.querySelector('a, .cell-main, .fn-item__name, .env-item__name');
        name = named ? named.textContent.trim().slice(0, 24) : '';
      }
      row.style.display = 'none';
      row.dispatchEvent(new CustomEvent('ux:remove', { bubbles: true }));
      toast((name ? '已删除「' + name + '」' : '已删除'), {
        type: 'danger',
        duration: seconds * 1000,
        actionLabel: '撤销',
        onAction: function () {
          row.style.display = '';
          row.dispatchEvent(new CustomEvent('ux:restore', { bubbles: true }));
          toast('已撤销删除', { type: 'success' });
        },
      });
    });
  }

  /* ---------------- 底部批量操作条 ---------------- */
  function bindBatch(scope) {
    var bar = document.querySelector(scope.getAttribute('data-ux-batchbar'));
    if (!bar) return;
    var countEl = bar.querySelector('[data-ux-batch-count]');
    var checkboxes = scope.querySelectorAll('.checkbox');

    function refresh() {
      var n = scope.querySelectorAll('.checkbox--on').length;
      if (countEl) countEl.textContent = n;
      bar.hidden = n === 0;
    }

    checkboxes.forEach(function (box) {
      box.addEventListener('click', function () {
        box.classList.toggle('checkbox--on');
        refresh();
      });
    });

    var clearBtn = bar.querySelector('[data-ux-batch-clear]');
    if (clearBtn) {
      clearBtn.addEventListener('click', function () {
        scope.querySelectorAll('.checkbox--on').forEach(function (b) { b.classList.remove('checkbox--on'); });
        refresh();
      });
    }

    // 批量删除：隐藏选中行并提供撤销（演示口径与单行删除一致）
    bar.querySelectorAll('[data-ux-batch-delete]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var rows = [];
        scope.querySelectorAll('.checkbox--on').forEach(function (box) {
          var row = box.closest('tr');
          if (row && row.style.display !== 'none') rows.push(row);
        });
        if (!rows.length) return;
        rows.forEach(function (r) {
          r.style.display = 'none';
          r.dispatchEvent(new CustomEvent('ux:remove', { bubbles: true }));
        });
        scope.querySelectorAll('.checkbox--on').forEach(function (b) { b.classList.remove('checkbox--on'); });
        refresh();
        toast('已删除 ' + rows.length + ' 项', {
          type: 'danger',
          duration: 5000,
          actionLabel: '撤销',
          onAction: function () {
            rows.forEach(function (r) {
              r.style.display = '';
              r.dispatchEvent(new CustomEvent('ux:restore', { bubbles: true }));
            });
            toast('已撤销删除', { type: 'success' });
            refresh();
          },
        });
      });
    });

    // 其余批量动作：统一结果反馈（含选中数量）
    bar.querySelectorAll('[data-ux-batch-action]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var n = scope.querySelectorAll('.checkbox--on').length;
        if (!n) return;
        toast(btn.getAttribute('data-ux-batch-action') + '：' + n + ' 项', { type: 'success' });
        scope.querySelectorAll('.checkbox--on').forEach(function (b) { b.classList.remove('checkbox--on'); });
        refresh();
      });
    });
    refresh();
  }

  /* ---------------- 筛选与页码恢复 ---------------- */
  function bindFilters(form) {
    var key = form.getAttribute('data-ux-filters');
    if (!key) return;
    var restoreChip = document.querySelector('[data-ux-restore-for="' + key + '"]');
    var clearBtn = restoreChip && restoreChip.querySelector('[data-ux-restore-clear]');

    function serialize() {
      var fields = [];
      form.querySelectorAll('input, select').forEach(function (f, i) {
        fields.push({ i: i, v: f.value, tag: f.tagName });
      });
      var pages = document.querySelectorAll('[data-ux-pageable] .page-btn');
      var active = -1;
      pages.forEach(function (b, i) { if (b.classList.contains('page-btn--active')) active = i; });
      return JSON.stringify({ f: fields, p: active });
    }

    function applySaved(saved) {
      var data;
      try { data = JSON.parse(saved); } catch (e) { return false; }
      var fields = form.querySelectorAll('input, select');
      var changed = false;
      (data.f || []).forEach(function (rec) {
        var f = fields[rec.i];
        if (f && f.value !== rec.v) { f.value = rec.v; changed = true; }
      });
      if (data.p >= 0) {
        var pages = document.querySelectorAll('[data-ux-pageable] .page-btn');
        pages.forEach(function (b, i) {
          b.classList.toggle('page-btn--active', i === data.p);
          if (i === data.p) changed = true;
        });
      }
      return changed;
    }

    var saved = store.get(key);
    if (saved && applySaved(saved) && restoreChip) {
      restoreChip.hidden = false;
    }

    if (clearBtn) {
      clearBtn.addEventListener('click', function () {
        store.remove(key);
        restoreChip.hidden = true;
        toast('已清除恢复的筛选条件', {});
      });
    }

    // 列表页无「查询/重置」按钮：筛选变更即生效，故改选项/回车失焦后即存
    form.querySelectorAll('input, select').forEach(function (f) {
      f.addEventListener('change', function () { store.set(key, serialize()); });
    });
    // 离开列表（进详情）前保存，返回时恢复
    window.addEventListener('pagehide', function () { store.set(key, serialize()); });
  }

  /* ---------------- 内联错误条 + 重试 ---------------- */
  function bindErrorBar(bar) {
    var retry = bar.querySelector('[data-ux-retry]');
    if (!retry) return;
    retry.addEventListener('click', function () {
      retry.disabled = true;
      retry.textContent = '重试中…';
      window.setTimeout(function () {
        bar.hidden = true;
        retry.disabled = false;
        retry.textContent = '重试';
        toast('已重新加载最新数据', { type: 'success' });
      }, 700);
    });
    var dismiss = bar.querySelector('[data-ux-error-dismiss]');
    if (dismiss) {
      dismiss.addEventListener('click', function () { bar.hidden = true; });
    }
  }

  /* ---------------- 保存态按钮 ---------------- */
  function bindSaveBtn(btn) {
    btn.addEventListener('click', function () {
      if (btn.classList.contains('is-saving')) return;
      var original = btn.innerHTML;
      btn.classList.add('is-saving');
      btn.textContent = '保存中…';
      window.setTimeout(function () {
        btn.classList.remove('is-saving');
        btn.innerHTML = '<span class="btn__state">已保存 ✓</span>';
        toast('保存成功', { type: 'success' });
        window.setTimeout(function () { btn.innerHTML = original; }, 1800);
      }, 600);
    });
  }

  /* ---------------- 快捷键（data-ux-key="mod+enter"） ---------------- */
  var isMac = /Mac|iPhone|iPad/.test(navigator.platform || '');
  function bindShortcut(el) {
    var combo = (el.getAttribute('data-ux-key') || '').toLowerCase();
    if (!combo) return;
    document.addEventListener('keydown', function (e) {
      var mod = isMac ? e.metaKey : e.ctrlKey;
      if (combo === 'mod+enter' && mod && e.key === 'Enter') { e.preventDefault(); el.click(); }
      if (combo === 'mod+s' && mod && (e.key === 's' || e.key === 'S')) { e.preventDefault(); el.click(); }
    });
  }

  /* ---------------- 演示用 feedback（无真实行为的操作给出反馈） ---------------- */
  function bindFeedback(el) {
    el.addEventListener('click', function (e) {
      e.stopPropagation();
      toast(el.getAttribute('data-ux-feedback'), { type: el.getAttribute('data-ux-feedback-type') || '' });
    });
  }

  function init(scope) {
    var root = scope || document;
    root.querySelectorAll('[data-ux-toast]').forEach(function (el) {
      once(el, function () {
        el.addEventListener('click', function () {
          toast(el.getAttribute('data-ux-toast'), { type: el.getAttribute('data-ux-toast-type') || '' });
        });
      });
    });
    root.querySelectorAll('[data-ux-feedback]').forEach(function (el) { once(el, bindFeedback); });
    root.querySelectorAll('[data-ux-undo]').forEach(function (el) { once(el, bindUndo); });
    root.querySelectorAll('[data-ux-batchbar]').forEach(function (el) { once(el, bindBatch); });
    root.querySelectorAll('[data-ux-filters]').forEach(function (el) { once(el, bindFilters); });
    root.querySelectorAll('.ux-errorbar').forEach(function (el) { once(el, bindErrorBar); });
    root.querySelectorAll('[data-ux-save]').forEach(function (el) { once(el, bindSaveBtn); });
    root.querySelectorAll('[data-ux-key]').forEach(function (el) { once(el, bindShortcut); });
  }

  // 供页面动态插入的节点复用同一套交互（内部按 __uxBound 去重）
  window.RTUX = { toast: toast, store: store, isMac: isMac, bind: init };

  if (document.readyState === 'loading') {
    // 直接把 init 当监听器会把 Event 当根节点，静态 data-ux-* 钩子全部绑不上
    document.addEventListener('DOMContentLoaded', function () { init(); });
  } else {
    init();
  }
})();
