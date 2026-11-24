// 主题切换、删除确认、编辑器小工具。没有框架，也就这么点东西。
(function () {
  'use strict';

  var root = document.documentElement;
  var KEY = 'mmb-theme';

  // ---------- 深浅色 ----------
  var toggle = document.getElementById('theme-toggle');
  if (toggle) {
    var resolve = function () {
      return root.dataset.theme === 'light' ? 'light' : 'dark';
    };
    var sync = function () {
      var dark = resolve() === 'dark';
      var icon = toggle.querySelector('.ts-icon');
      var label = toggle.querySelector('.ts-label');
      if (icon) icon.textContent = dark ? '☾' : '☀';
      if (label) label.textContent = dark ? '深色' : '浅色';
      toggle.setAttribute('title', dark ? '切到浅色' : '切到深色');
      toggle.setAttribute('aria-pressed', String(!dark));
    };
    sync();
    toggle.addEventListener('click', function () {
      var next = resolve() === 'dark' ? 'light' : 'dark';
      root.dataset.theme = next;
      try {
        localStorage.setItem(KEY, next);
      } catch (e) {
        /* 隐私模式下就算了 */
      }
      sync();
    });
  }

  // ---------- 危险动作二次确认 ----------
  document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      if (!window.confirm(form.dataset.confirm || '确定？')) event.preventDefault();
    });
  });

  // ---------- 发动态：选图预览 ----------
  var photoInput = document.getElementById('photo-input');
  var composerPreview = document.getElementById('composer-preview');
  var pickHint = document.getElementById('pick-hint');
  var MAX_PHOTOS = 9;

  if (photoInput) {
    photoInput.addEventListener('change', function () {
      var files = Array.prototype.slice.call(photoInput.files || []).slice(0, MAX_PHOTOS);
      composerPreview.innerHTML = '';
      files.forEach(function (file) {
        var img = document.createElement('img');
        img.src = URL.createObjectURL(file);
        composerPreview.appendChild(img);
      });
      composerPreview.hidden = files.length === 0;
      if (pickHint) {
        pickHint.textContent = files.length
          ? '已选 ' + files.length + ' 张'
          : '最多 9 张，单张 10MB 以内';
      }
    });
  }

  // ---------- 点开看大图 ----------
  var overlay = null;
  var picture = null;
  var boxUrls = [];
  var boxIndex = 0;

  var closeBox = function () {
    if (!overlay) return;
    overlay.remove();
    overlay = null;
    picture = null;
    document.body.style.overflow = '';
  };

  var openAt = function (i) {
    if (!boxUrls.length) return;
    boxIndex = (i + boxUrls.length) % boxUrls.length;
    if (!overlay) {
      overlay = document.createElement('div');
      overlay.className = 'lightbox';

      picture = document.createElement('img');
      overlay.appendChild(picture);

      var closeBtn = document.createElement('button');
      closeBtn.type = 'button';
      closeBtn.className = 'lightbox-close';
      closeBtn.textContent = '×';
      closeBtn.addEventListener('click', closeBox);
      overlay.appendChild(closeBtn);

      if (boxUrls.length > 1) {
        var prev = document.createElement('button');
        prev.type = 'button';
        prev.className = 'lightbox-prev';
        prev.textContent = '‹';
        prev.addEventListener('click', function (e) {
          e.stopPropagation();
          openAt(boxIndex - 1);
        });
        overlay.appendChild(prev);

        var next = document.createElement('button');
        next.type = 'button';
        next.className = 'lightbox-next';
        next.textContent = '›';
        next.addEventListener('click', function (e) {
          e.stopPropagation();
          openAt(boxIndex + 1);
        });
        overlay.appendChild(next);
      }

      overlay.addEventListener('click', function (event) {
        if (event.target === overlay) closeBox();
      });

      document.body.appendChild(overlay);
      document.body.style.overflow = 'hidden';
    }
    picture.src = boxUrls[boxIndex];
  };

  var openBox = function (urls, i) {
    boxUrls = urls;
    openAt(i);
  };

  document.addEventListener('keydown', function (event) {
    if (!overlay) return;
    if (event.key === 'Escape') closeBox();
    if (event.key === 'ArrowLeft') openAt(boxIndex - 1);
    if (event.key === 'ArrowRight') openAt(boxIndex + 1);
  });

  var imageLinks = Array.prototype.slice.call(document.querySelectorAll('[data-lightbox]'));
  if (imageLinks.length) {
    var linkUrls = imageLinks.map(function (link) {
      return link.dataset.full;
    });
    imageLinks.forEach(function (link, i) {
      link.addEventListener('click', function (event) {
        event.preventDefault();
        openBox(linkUrls, i);
      });
    });
  }

  // 正文里插的图也能点开
  var proseImages = Array.prototype.slice.call(document.querySelectorAll('.prose img'));
  if (proseImages.length) {
    var proseUrls = proseImages.map(function (img) {
      return img.src;
    });
    proseImages.forEach(function (img, i) {
      img.classList.add('zoomable');
      img.addEventListener('click', function () {
        openBox(proseUrls, i);
      });
    });
  }

  // ---------- 登录页：点芯片开门 ----------
  var gateToggle = document.getElementById('gate-open');
  if (gateToggle) {
    var gateUser = document.querySelector('.gate-card input[name="username"]');

    var focusUser = function () {
      if (gateUser) gateUser.focus();
    };

    gateToggle.addEventListener('change', function () {
      if (gateToggle.checked) window.setTimeout(focusUser, 160);
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && gateToggle.checked) {
        gateToggle.checked = false;
        gateToggle.focus();
      }
    });

    // 密码错了回来时，卡片已经是展开的，直接把光标放进输入框
    if (gateToggle.checked) window.setTimeout(focusUser, 160);
  }

  // ---------- 学习计时：七段数码管 + 到点提醒 ----------
  (function () {
    var clock = document.getElementById('seg-clock');
    if (!clock) return;

    var remaining = Number(clock.dataset.remaining || '0');
    var sessionId = clock.dataset.id || '';
    var planned = Number(clock.dataset.minutes || '30');
    var meta = document.querySelector('meta[name="_csrf"]');
    var csrf = meta ? meta.getAttribute('content') : '';

    // 每个数字由哪几段亮着组成：a 上、b 右上、c 右下、d 下、e 左下、f 左上、g 中
    var SEGMENTS = {
      0: 'abcdef', 1: 'bc', 2: 'abged', 3: 'abgcd', 4: 'fgbc',
      5: 'afgcd', 6: 'afgedc', 7: 'abc', 8: 'abcdefg', 9: 'abcdfg'
    };
    // 七段的位置（viewBox 42 x 78）
    var RECTS = {
      a: [7, 2, 28, 6], b: [35, 8, 6, 27], c: [35, 42, 6, 27],
      d: [7, 70, 28, 6], e: [1, 42, 6, 27], f: [1, 8, 6, 27], g: [7, 36, 28, 6]
    };

    var withHours = planned >= 60;
    var digits = [];
    clock.innerHTML = '';

    function addDigit() {
      var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('viewBox', '0 0 42 78');
      svg.setAttribute('class', 'seg');
      var map = {};
      Object.keys(RECTS).forEach(function (key) {
        var box = RECTS[key];
        var rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        rect.setAttribute('x', box[0]);
        rect.setAttribute('y', box[1]);
        rect.setAttribute('width', box[2]);
        rect.setAttribute('height', box[3]);
        rect.setAttribute('rx', '3');
        svg.appendChild(rect);
        map[key] = rect;
      });
      clock.appendChild(svg);
      digits.push(map);
    }

    function addColon() {
      var colon = document.createElement('span');
      colon.className = 'seg-colon';
      colon.innerHTML = '<i></i><i></i>';
      clock.appendChild(colon);
    }

    // 60 分钟以上显示 H:MM:SS，否则 MM:SS
    if (withHours) {
      addDigit();
      addColon();
      addDigit();
      addDigit();
      addColon();
      addDigit();
      addDigit();
    } else {
      addDigit();
      addDigit();
      addColon();
      addDigit();
      addDigit();
    }

    function pad(value) {
      return value < 10 ? '0' + value : String(value);
    }

    function render(left) {
      var text;
      if (withHours) {
        text = Math.floor(left / 3600) + pad(Math.floor(left % 3600 / 60)) + pad(left % 60);
      } else {
        text = pad(Math.floor(left / 60)) + pad(left % 60);
      }
      for (var i = 0; i < digits.length; i++) {
        var lit = SEGMENTS[text.charAt(i)] || '';
        Object.keys(digits[i]).forEach(function (key) {
          digits[i][key].classList.toggle('on', lit.indexOf(key) >= 0);
        });
      }
    }

    var deadline = Date.now() + remaining * 1000;
    var loop = null;
    var finished = false;

    function tick() {
      var left = Math.max(0, Math.round((deadline - Date.now()) / 1000));
      render(left);
      if (left <= 0 && !finished) {
        finished = true;
        window.clearInterval(loop);
        done();
      }
    }

    function done() {
      fetch('/timer/finish', {
        method: 'POST',
        headers: {
          'X-CSRF-TOKEN': csrf,
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: 'id=' + encodeURIComponent(sessionId)
      }).catch(function () { });
      celebrate();
      showToast();
    }

    /** 页面里也能看到「时间到」，不依赖系统通知 */
    function showToast() {
      var toast = document.createElement('div');
      toast.className = 'timer-toast';

      var text = document.createElement('div');
      text.className = 'timer-toast-text';
      var title = document.createElement('b');
      title.textContent = '时间到';
      var sub = document.createElement('span');
      sub.textContent = '这段 ' + planned + ' 分钟跑完了，起来动一动。';
      text.appendChild(title);
      text.appendChild(sub);

      var button = document.createElement('button');
      button.type = 'button';
      button.className = 'btn btn-small';
      button.textContent = '知道了';
      button.addEventListener('click', function () {
        window.location.reload();
      });

      toast.appendChild(text);
      toast.appendChild(button);
      document.body.appendChild(toast);
    }

    function celebrate() {
      var original = document.title;
      var flip = false;
      var flash = window.setInterval(function () {
        flip = !flip;
        document.title = flip ? '⏰ 时间到' : original;
      }, 900);
      window.addEventListener('focus', function stop() {
        window.clearInterval(flash);
        document.title = original;
        window.removeEventListener('focus', stop);
      });

      try {
        if (window.Notification && Notification.permission === 'granted') {
          var note = new Notification('学习计时结束', {
            body: '这段 ' + planned + ' 分钟跑完了，起来动一动。'
          });
          note.onclick = function () {
            window.focus();
            note.close();
          };
        }
      } catch (e) {
        // 通知不可用还有标题和声音
      }

      try {
        var Ctx = window.AudioContext || window.webkitAudioContext;
        var ctx = new Ctx();
        [0, 260, 520].forEach(function (delay) {
          var osc = ctx.createOscillator();
          var gain = ctx.createGain();
          var at = ctx.currentTime + delay / 1000;
          osc.type = 'sine';
          osc.frequency.value = 880;
          gain.gain.setValueAtTime(0.0001, at);
          gain.gain.exponentialRampToValueAtTime(0.22, at + 0.02);
          gain.gain.exponentialRampToValueAtTime(0.0001, at + 0.32);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(at);
          osc.stop(at + 0.36);
        });
      } catch (e) {
        // 没有音频环境就算了
      }
    }

    render(remaining);
    loop = window.setInterval(tick, 250);
    tick();

    // 点「开始」时先要通知权限：等用户选完再提交表单
    // （如果一边请求一边提交，页面马上跳走，授权框会被取消，权限一直是 default，
    //  到点就只能听到声音、看不到弹窗 —— 之前就是这个坑）
    var startForm = document.querySelector('.timer-start');
    if (startForm && window.Notification) {
      startForm.addEventListener('submit', function (event) {
        if (Notification.permission !== 'default') {
          return;
        }
        event.preventDefault();
        var go = function () {
          startForm.submit();
        };
        Notification.requestPermission().then(go, go);
      });
    }

    // 已经被拒过一次：在页面上说清楚，别让人等到点才发现没弹窗
    var notifyHint = document.getElementById('notify-hint');
    if (notifyHint && window.Notification && Notification.permission === 'denied') {
      notifyHint.textContent = '浏览器通知被拒绝了，到点只在页面里提示。点地址栏左边的小锁可以改回来。';
      notifyHint.hidden = false;
    }
  })();

  // ---------- 编辑器 ----------
  var editorForm = document.getElementById('editor-form');
  if (!editorForm) return;

  var kindInput = document.getElementById('kind-input');
  var kindBtns = Array.prototype.slice.call(document.querySelectorAll('.kind-opt'));
  kindBtns.forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (kindInput) kindInput.value = btn.dataset.kind;
      kindBtns.forEach(function (b) {
        b.classList.toggle('on', b === btn);
      });
    });
  });

  var textarea = document.getElementById('bodyMd');
  if (!textarea) return;

  var BLOCK_MARKS = ['## ', '- ', '> '];

  document.querySelectorAll('.toolbtn[data-md]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var mark = btn.dataset.md;
      var placeholder = btn.dataset.placeholder || '';
      var start = textarea.selectionStart;
      var end = textarea.selectionEnd;

      if (BLOCK_MARKS.indexOf(mark) >= 0) {
        var lineStart = textarea.value.lastIndexOf('\n', start - 1) + 1;
        var lineEnd = textarea.value.indexOf('\n', lineStart);
        if (lineEnd < 0) lineEnd = textarea.value.length;
        var lineText = textarea.value.slice(lineStart, lineEnd);
        var picked = textarea.value.slice(start, end) || lineText || placeholder;
        textarea.setRangeText(mark + picked, lineStart, Math.max(end, lineStart), 'end');
      } else {
        var sel = textarea.value.slice(start, end) || placeholder;
        textarea.setRangeText(mark + sel + mark, start, end, 'end');
      }
      textarea.focus();
      textarea.dispatchEvent(new Event('input'));
    });
  });

  // 实时预览：发给服务端渲染，保证和最终页面一致
  var preview = document.getElementById('preview');
  var previewToggle = document.getElementById('preview-toggle');
  var meta = document.querySelector('meta[name="_csrf"]');
  var csrf = meta ? meta.getAttribute('content') : '';
  var timer = null;

  var refreshPreview = function () {
    if (!preview || preview.hidden) return;
    var body = new URLSearchParams({ bodyMd: textarea.value }).toString();
    fetch('/admin/preview', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
        'X-CSRF-TOKEN': csrf
      },
      body: body
    })
      .then(function (res) {
        return res.ok ? res.text() : '预览暂时不可用。';
      })
      .then(function (html) {
        preview.innerHTML = html || '<p class="preview-empty">还没写东西。</p>';
      })
      .catch(function () {
        preview.innerHTML = '<p class="preview-empty">预览暂时不可用。</p>';
      });
  };

  if (previewToggle && preview) {
    previewToggle.classList.add('righthand');
    previewToggle.addEventListener('click', function () {
      preview.hidden = !preview.hidden;
      previewToggle.textContent = preview.hidden ? '预览' : '收起预览';
      if (!preview.hidden) refreshPreview();
    });
  }

  textarea.addEventListener('input', function () {
    window.clearTimeout(timer);
    timer = window.setTimeout(refreshPreview, 350);
  });

  // 插图：传到 /admin/archives/{id}/images，再把 Markdown 图片插到光标处
  var imageBtn = document.getElementById('doc-image');
  var imageFile = document.getElementById('doc-file');
  var uploadUrl = editorForm.dataset.uploadUrl;
  if (imageBtn && imageFile && uploadUrl) {
    imageBtn.addEventListener('click', function () {
      imageFile.click();
    });

    imageFile.addEventListener('change', function () {
      var file = imageFile.files && imageFile.files[0];
      if (!file) return;

      var data = new FormData();
      data.append('file', file);
      imageBtn.disabled = true;
      imageBtn.textContent = '上传中…';

      fetch(uploadUrl, { method: 'POST', headers: { 'X-CSRF-TOKEN': csrf }, body: data })
        .then(function (res) {
          if (!res.ok) throw new Error('upload failed');
          return res.json();
        })
        .then(function (info) {
          var alt = (file.name || '图').replace(/\.[^.]+$/, '');
          var markdown = '![' + alt + '](' + info.url + ')';
          textarea.setRangeText(markdown, textarea.selectionStart, textarea.selectionEnd, 'end');
          textarea.focus();
          textarea.dispatchEvent(new Event('input'));
        })
        .catch(function () {
          window.alert('图片没传上去，换个格式或者小一点的试试。');
        })
        .then(function () {
          imageBtn.disabled = false;
          imageBtn.textContent = '插图';
          imageFile.value = '';
        });
    });
  }

  // Ctrl / ⌘ + S 保存
  editorForm.addEventListener('keydown', function (event) {
    if ((event.metaKey || event.ctrlKey) && String(event.key).toLowerCase() === 's') {
      event.preventDefault();
      if (editorForm.requestSubmit) editorForm.requestSubmit();
      else editorForm.submit();
    }
  });
})();
