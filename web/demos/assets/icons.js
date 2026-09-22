/**
 * 线条扁平化图标库（24 网格 / stroke 1.5 / 圆角端点）
 * 演示页通过 <span data-icon="name"></span> 声明，加载时统一替换为内联 SVG
 */
(function () {
  const P = {
    // 导航 / 菜单
    gauge: '<path d="M3 13a9 9 0 0 1 18 0"/><path d="M12 13l4-4"/><circle cx="12" cy="13" r="1.5"/>',
    user: '<circle cx="12" cy="8" r="3.5"/><path d="M4.5 20a7.5 7.5 0 0 1 15 0"/>',
    building: '<rect x="4" y="3" width="16" height="18" rx="1.5"/><path d="M9 7h2M13 7h2M9 11h2M13 11h2M9 15h2M13 15h2"/><path d="M10 21v-3h4v3"/>',
    lock: '<rect x="5" y="10.5" width="14" height="9.5" rx="1.5"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/><circle cx="12" cy="15.2" r="1.3"/>',
    sparkle: '<path d="M12 3.5l1.7 4.8 4.8 1.7-4.8 1.7L12 16.5l-1.7-4.8L5.5 10l4.8-1.7z"/><path d="M18.5 16.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z"/>',
    folder: '<path d="M3.5 6.5A1.5 1.5 0 0 1 5 5h4l2 2.5h8A1.5 1.5 0 0 1 20.5 9v8.5A1.5 1.5 0 0 1 19 19H5a1.5 1.5 0 0 1-1.5-1.5z"/>',
    'folder-open': '<path d="M3.5 6.5A1.5 1.5 0 0 1 5 5h4l2 2.5h8A1.5 1.5 0 0 1 20.5 9v1.5"/><path d="M3.5 17.5V7.5"/><path d="M4 19l2.2-7.5h15L19 19z"/>',
    monitor: '<rect x="3" y="4.5" width="18" height="12" rx="1.5"/><path d="M9 20h6M12 16.5V20"/>',
    bell: '<path d="M6.5 9.5a5.5 5.5 0 0 1 11 0c0 4 1.5 5.5 1.5 5.5H5s1.5-1.5 1.5-5.5z"/><path d="M10 18.5a2 2 0 0 0 4 0"/>',
    settings:
      '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5v.2a2 2 0 1 1-4 0v-.1a1.6 1.6 0 0 0-1-1.5 1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1h.2a2 2 0 1 1 0 4H21a1.6 1.6 0 0 0-1.5 1z"/>',
    'chevron-down': '<path d="M6 9.5l6 6 6-6"/>',
    'chevron-right': '<path d="M9.5 6l6 6-6 6"/>',
    'chevron-left': '<path d="M14.5 6l-6 6 6 6"/>',
    'arrow-left': '<path d="M19 12H5"/><path d="M11 6l-6 6 6 6"/>',
    'arrow-right': '<path d="M5 12h14"/><path d="M13 6l6 6-6 6"/>',
    menu: '<path d="M4 6.5h16M4 12h16M4 17.5h16"/>',

    // 操作
    plus: '<path d="M12 5v14M5 12h14"/>',
    search: '<circle cx="10.8" cy="10.8" r="6.3"/><path d="M15.5 15.5L20 20"/>',
    filter: '<path d="M4 5.5h16l-6.2 7.3v5.4l-3.6 2v-7.4z"/>',
    refresh: '<path d="M20 12a8 8 0 1 1-2.3-5.6"/><path d="M20 4v5h-5"/>',
    edit: '<path d="M4 20h4l10-10-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
    trash: '<path d="M4.5 6.5h15"/><path d="M9 6.5V5a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 5v1.5"/><path d="M6.5 6.5l1 13h9l1-13"/><path d="M10 10v6M14 10v6"/>',
    copy: '<rect x="8.5" y="8.5" width="11" height="11" rx="1.5"/><path d="M5.5 15.5A1.5 1.5 0 0 1 4.5 14V5.5A1.5 1.5 0 0 1 6 4h8.5A1.5 1.5 0 0 1 15.5 5.5"/>',
    download: '<path d="M12 4v11"/><path d="M7.5 10.5L12 15l4.5-4.5"/><path d="M4.5 19.5h15"/>',
    upload: '<path d="M12 15V4"/><path d="M7.5 8.5L12 4l4.5 4.5"/><path d="M4.5 19.5h15"/>',
    'more-h': '<circle cx="5.5" cy="12" r="1.4"/><circle cx="12" cy="12" r="1.4"/><circle cx="18.5" cy="12" r="1.4"/>',
    external: '<path d="M14 4.5h5.5V10"/><path d="M19.5 4.5L11 13"/><path d="M19.5 14v4.5A1.5 1.5 0 0 1 18 20H6a1.5 1.5 0 0 1-1.5-1.5v-12A1.5 1.5 0 0 1 6 5h4.5"/>',
    eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',

    // 状态（图形与颜色解耦：形状本身传达语义）
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    'check-circle': '<circle cx="12" cy="12" r="8.5"/><path d="M8.2 12.3l2.6 2.6 5-5.4"/>',
    x: '<path d="M6 6l12 12M18 6L6 18"/>',
    'x-circle': '<circle cx="12" cy="12" r="8.5"/><path d="M9 9l6 6M15 9l-6 6"/>',
    'stop-circle': '<circle cx="12" cy="12" r="8.5"/><rect x="9" y="9" width="6" height="6" rx="1"/>',
    'alert-triangle': '<path d="M12 4.5l8.5 15h-17z"/><path d="M12 10v4"/><circle cx="12" cy="16.8" r="0.9" fill="currentColor" stroke="none"/>',
    'alert-circle': '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.8v5"/><circle cx="12" cy="16.2" r="0.9" fill="currentColor" stroke="none"/>',
    circle: '<circle cx="12" cy="12" r="8.5"/>',
    'clock-circle': '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    'loader': '<path d="M12 3.5v4M12 16.5v4M3.5 12h4M16.5 12h4M6 6l2.8 2.8M15.2 15.2L18 18M18 6l-2.8 2.8M8.8 15.2L6 18"/>',
    play: '<circle cx="12" cy="12" r="8.5"/><path d="M10 8.5l6 3.5-6 3.5z"/>',
    pause: '<circle cx="12" cy="12" r="8.5"/><path d="M10 9v6M14 9v6"/>',
    ban: '<circle cx="12" cy="12" r="8.5"/><path d="M6 18L18 6"/>',

    // 业务
    document: '<path d="M6 3.5h8L18.5 8v12.5H6z"/><path d="M14 3.5V8h4.5"/><path d="M9 12.5h6M9 16h6"/>',
    tickets: '<rect x="3.5" y="6" width="17" height="12" rx="1.5"/><path d="M8 6v12" stroke-dasharray="2 2.4"/><path d="M11.5 10.5h6M11.5 14h4"/>',
    calendar: '<rect x="3.5" y="5.5" width="17" height="15" rx="1.5"/><path d="M3.5 10h17M8 3.5v4M16 3.5v4"/>',
    link: '<path d="M10.5 13.5a4 4 0 0 0 5.7 0l2.8-2.8a4 4 0 1 0-5.7-5.7l-1.5 1.5"/><path d="M13.5 10.5a4 4 0 0 0-5.7 0l-2.8 2.8a4 4 0 1 0 5.7 5.7l1.5-1.5"/>',
    cpu: '<rect x="6.5" y="6.5" width="11" height="11" rx="1.5"/><rect x="9.8" y="9.8" width="4.4" height="4.4" rx="0.6"/><path d="M9.5 3.5v3M14.5 3.5v3M9.5 17.5v3M14.5 17.5v3M3.5 9.5h3M3.5 14.5h3M17.5 9.5h3M17.5 14.5h3"/>',
    box: '<path d="M12 3.8l8 4v8.4l-8 4-8-4V7.8z"/><path d="M4 7.8l8 4 8-4M12 11.8v8.4"/>',
    compass: '<circle cx="12" cy="12" r="8.5"/><path d="M15.5 8.5l-2 5-5 2 2-5z"/>',
    timer: '<circle cx="12" cy="13.5" r="7"/><path d="M12 10v3.5l2.4 1.6"/><path d="M9.5 3.5h5"/><path d="M12 3.5V6"/>',
    zap: '<path d="M13 3L5.5 13.5H12L11 21l7.5-10.5H12z"/>',
    connection: '<circle cx="5.5" cy="12" r="2.2"/><circle cx="18.5" cy="6.5" r="2.2"/><circle cx="18.5" cy="17.5" r="2.2"/><path d="M7.5 11l9-3.6M7.5 13l9 3.6"/>',
    operation: '<circle cx="12" cy="5.5" r="2.2"/><circle cx="5.5" cy="18" r="2.2"/><circle cx="18.5" cy="18" r="2.2"/><path d="M12 7.7v4.1M12 11.8L6.8 16.4M12 11.8l5.2 4.6"/>',
    'data-chart': '<path d="M4 20h16"/><rect x="5.5" y="12" width="3.4" height="5.5" rx="0.6"/><rect x="10.3" y="8" width="3.4" height="9.5" rx="0.6"/><rect x="15.1" y="5" width="3.4" height="12.5" rx="0.6"/>',
    shield: '<path d="M12 3.5l7 2.6v5.5c0 4.3-3 7.4-7 8.9-4-1.5-7-4.6-7-8.9V6.1z"/><path d="M9 12l2.2 2.2L15.5 10"/>',
    bug: '<rect x="8" y="7.5" width="8" height="10.5" rx="4"/><path d="M8 11H5M8 14.5H5.2M16 11h3M16 14.5h2.8M9.5 7.5L8 5.2M14.5 7.5L16 5.2"/><path d="M12 10.5v5"/>',
    layers: '<path d="M12 3.5l8.5 4.5L12 12.5 3.5 8z"/><path d="M3.5 12.5L12 17l8.5-4.5M3.5 16.5L12 21l8.5-4.5"/>',
    message: '<path d="M4.5 5.5h15v10.5H9l-4.5 3.5z"/><path d="M8 9.5h8M8 12.5h5"/>',
    userfilled: '<circle cx="12" cy="8" r="3.5"/><path d="M4.5 20a7.5 7.5 0 0 1 15 0"/>',
    'info-filled': '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.2"/><circle cx="12" cy="8" r="0.9" fill="currentColor" stroke="none"/>',
    switch: '<path d="M4 7.5h13M14 4.5l3 3-3 3"/><path d="M20 16.5H7M10 13.5l-3 3 3 3"/>',
    key: '<circle cx="8" cy="14.5" r="4"/><path d="M11 11.5L19.5 3M16.5 6l2.5 2.5M14.5 8l2 2"/>',
    star: '<path d="M12 4l2.4 4.9 5.4.8-3.9 3.8.9 5.4-4.8-2.5-4.8 2.5.9-5.4L4.2 9.7l5.4-.8z"/>',
    grid: '<rect x="4" y="4" width="7" height="7" rx="1"/><rect x="13" y="4" width="7" height="7" rx="1"/><rect x="4" y="13" width="7" height="7" rx="1"/><rect x="13" y="13" width="7" height="7" rx="1"/>',
    list: '<path d="M8.5 6.5h12M8.5 12h12M8.5 17.5h12"/><circle cx="4.5" cy="6.5" r="1.2"/><circle cx="4.5" cy="12" r="1.2"/><circle cx="4.5" cy="17.5" r="1.2"/>',
    terminal: '<rect x="3.5" y="4.5" width="17" height="15" rx="1.5"/><path d="M7 9.5l3 2.5-3 2.5M12.5 15h4.5"/>',
    cloud: '<path d="M7.5 18.5a4 4 0 0 1-.4-8A5.5 5.5 0 0 1 17.8 11a3.8 3.8 0 0 1-.3 7.5z"/>',
    home: '<path d="M4 11l8-6.5 8 6.5"/><path d="M6 9.8V19h12V9.8"/><path d="M10 19v-5h4v5"/>',
    tag: '<path d="M4 4.5h7.2l8.3 8.3-6.7 6.7L4.5 11.2z"/><circle cx="8.3" cy="8.3" r="1.3"/>',
  };

  function svg(name) {
    const body = P[name];
    if (!body) return '';
    return (
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" ' +
      'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      body +
      '</svg>'
    );
  }

  function hydrate(root) {
    (root || document)
      .querySelectorAll('[data-icon]')
      .forEach(function (el) {
        const html = svg(el.getAttribute('data-icon'));
        if (html) el.innerHTML = html;
      });
  }

  window.RTIcons = { hydrate: hydrate, svg: svg };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      hydrate(document);
    });
  } else {
    hydrate(document);
  }
})();
