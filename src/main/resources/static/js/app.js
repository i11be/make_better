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
