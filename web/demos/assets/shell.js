/**
 * 演示页布局壳注入
 * 依据 <body> 的 data-shell / data-mode / data-active / data-workspace / data-project
 * 渲染深色顶栏（与 AdminLayout / BusinessLayout 结构一致）与管理端侧边栏
 */
(function () {
  const body = document.body;
  const shell = body.getAttribute('data-shell');

  if (!shell || shell === 'bare' || !window.RTIcons) {
    return;
  }

  const mode = body.getAttribute('data-mode') || 'none';
  const active = body.getAttribute('data-active') || '';
  const workspace = body.getAttribute('data-workspace') || '';
  const project = body.getAttribute('data-project') || '';

  function icon(name) {
    return '<span data-icon="' + name + '"></span>';
  }

  /* ---------- 动态菜单（BusinessLayout 顶部） ---------- */
  const MENUS = {
    workspace: [
      { key: 'info', label: '空间信息', icon: 'info-filled' },
      { key: 'members', label: '成员管理', icon: 'userfilled' },
      { key: 'projects', label: '项目列表', icon: 'folder' },
    ],
    project: [
      { key: 'ft', label: '功能测试', icon: 'monitor' },
      { key: 'bugs', label: '缺陷管理', icon: 'bug' },
      { key: 'api', label: '接口测试', icon: 'connection' },
    ],
  };

  function menuHtml() {
    if (shell === 'admin') return '';
    const items = MENUS[mode];
    if (!items) {
      return '<div class="topbar__menu"><span class="topbar__menu-hint">请选择一个工作空间</span></div>';
    }
    let html = '<div class="topbar__menu">';
    items.forEach(function (it) {
      html +=
        '<div class="menu-item' +
        (active === it.key ? ' menu-item--active' : '') +
        '">' +
        icon(it.icon) +
        '<span>' +
        it.label +
        '</span></div>';
    });
    return html + '</div>';
  }

  /* ---------- 上下文标签 ---------- */
  function tagsHtml() {
    let html = '';
    if (shell === 'admin') {
      html += '<span class="shell-tag shell-tag--solid">系统管理</span>';
      return '<div class="topbar__tags">' + html + '</div>';
    }
    if (workspace && (mode === 'workspace' || mode === 'project')) {
      html += '<span class="shell-tag shell-tag--brand">' + workspace + '</span>';
    }
    if (project && mode === 'project') {
      html += '<span class="shell-tag shell-tag--neutral">' + project + '</span>';
    }
    return html ? '<div class="topbar__tags">' + html + '</div>' : '';
  }

  /* ---------- 右侧入口 ---------- */
  function rightIconsHtml() {
    let html = '<div class="topbar__icons">';
    html += '<div class="icon-btn">' + icon('folder-open') + '<span>我的空间</span></div>';
    if (mode === 'project') {
      html += '<div class="icon-btn icon-btn--active">' + icon('folder') + '<span>我的项目</span></div>';
    }
    if (mode === 'workspace' || mode === 'project') {
      html += '<div class="icon-btn">' + icon('settings') + '<span>空间管理</span></div>';
    }
    const adminActive = shell === 'admin';
    html +=
      '<div class="icon-btn' +
      (adminActive ? ' icon-btn--active' : '') +
      '">' +
      icon('monitor') +
      '<span>系统管理</span></div>';
    html += '<div class="icon-btn"><span class="dot-badge">' + icon('bell') + '</span><span>消息中心</span></div>';
    html += '<span class="shell-divider"></span>';
    html +=
      '<div class="topbar__user"><span class="avatar avatar--brand">张</span>' +
      '<span class="topbar__username">张明</span>' +
      icon('chevron-down') +
      '</div>';
    return html + '</div>';
  }

  /* ---------- 顶栏 ---------- */
  const topbar =
    '<header class="topbar">' +
    '<div class="topbar__logo"><span class="topbar__logo-mark">' + icon('zap') + '</span>' +
    '<span class="topbar__logo-text">RoboTest</span></div>' +
    tagsHtml() +
    menuHtml() +
    rightIconsHtml() +
    '</header>';

  /* ---------- 管理端侧边栏（概览置顶不入组；功能按组织与权限/平台配置分组，角色管理与用户管理相邻） ---------- */
  const ADMIN_SIDE = [
    {
      items: [
        { key: 'dashboard', label: '数据概览', icon: 'gauge' },
      ],
    },
    {
      title: '组织与权限',
      items: [
        { key: 'users', label: '用户管理', icon: 'user' },
        { key: 'roles', label: '角色管理', icon: 'lock' },
        { key: 'workspaces', label: '空间管理', icon: 'building' },
      ],
    },
    {
      title: '平台配置',
      items: [
        { key: 'ai-config', label: 'AI 配置', icon: 'sparkle' },
      ],
    },
  ];

  function sideHtml() {
    if (shell !== 'admin') return '';
    let html = '<aside class="side">';
    ADMIN_SIDE.forEach(function (section) {
      if (section.title) {
        html += '<div class="side__group-title">' + section.title + '</div>';
      }
      section.items.forEach(function (it) {
        html +=
          '<div class="menu-item side__item' +
          (active === it.key ? ' menu-item--active' : '') +
          '">' +
          icon(it.icon) +
          '<span>' +
          it.label +
          '</span></div>';
      });
    });
    return html + '</aside>';
  }

  body.insertAdjacentHTML('afterbegin', topbar + sideHtml());
  window.RTIcons.hydrate(document);
})();
