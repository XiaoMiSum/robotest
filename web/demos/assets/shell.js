/**
 * 演示页布局壳注入
 * 依据 <body> 的 data-shell / data-mode / data-active / data-workspace / data-project
 * 渲染深色顶栏（与 AdminLayout / BusinessLayout 结构一致）与管理端侧边栏；
 * project 页左侧导航由 <aside class="split__side" data-side="..."> 占位统一渲染。
 * 全部页面跳转只注册在下方 ROUTES / SIDES（单一事实源），页面 HTML 不再逐页维护导航标记。
 */
(function () {
  const body = document.body;
  const shell = body.getAttribute('data-shell');

  if (!shell || shell === 'bare' || !window.RTIcons) {
    return;
  }

  // 由脚本自身位置（demos/assets/）推导 demos 根：任意子目录页面都无需手写 ../ 相对路径
  const BASE = new URL('../', document.currentScript.src);

  const mode = body.getAttribute('data-mode') || 'none';
  const active = body.getAttribute('data-active') || '';
  const workspace = body.getAttribute('data-workspace') || '';
  const project = body.getAttribute('data-project') || '';

  function icon(name) {
    return '<span data-icon="' + name + '"></span>';
  }

  /* ---------- 路由注册表（跳转单一事实源）：key → demos 根相对路径 ---------- */
  const ROUTES = {
    // 顶栏右侧入口
    'my-workspaces': 'workspace/list.html',
    'my-projects': 'workspace/projects.html',
    'workspace-manage': 'workspace/info.html',
    'system-admin': 'admin/dashboard.html',
    // logo（业务端 goHome：项目模式回工作台，否则回空间列表；管理端 logo 在真实应用中不可点）
    'project-home': 'project/dashboard.html',
    // 顶栏动态菜单（与 data-active 同名，高亮与跳转共用 key）
    info: 'workspace/info.html',
    members: 'workspace/members.html',
    projects: 'workspace/projects.html',
    ft: 'project/functional-testing.html',
    bugs: 'project/bugs.html',
    api: 'project/api-testing.html',
    // 管理端侧边栏（key 与页面 data-active 一致，高亮与跳转共用）
    dashboard: 'admin/dashboard.html',
    users: 'admin/users.html',
    roles: 'admin/roles.html',
    workspaces: 'admin/workspaces.html',
    // project 页左侧导航
    'ft-cases': 'project/functional-testing.html',
    'ft-reviews': 'project/reviews.html',
    'ft-plans': 'project/plans.html',
    'ft-requirements': 'project/requirements.html',
    'api-debug': 'project/api-debug.html',
    'api-interfaces': 'project/api-testing.html',
    'api-mocks': 'project/api-mocks.html',
    'api-scenes': 'project/api-scenes.html',
    'api-reports': 'project/api-reports.html',
    'api-schedules': 'project/api-schedules.html',
    'api-environments': 'project/api-environments.html',
    'api-gitlab': 'project/api-gitlab.html',
    'api-functions': 'project/api-functions.html',
    'api-components': 'project/api-components.html',
  };

  function href(key) {
    return new URL(ROUTES[key], BASE).href;
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
        '<a class="menu-item' +
        (active === it.key ? ' menu-item--active' : '') +
        '" href="' +
        href(it.key) +
        '">' +
        icon(it.icon) +
        '<span>' +
        it.label +
        '</span></a>';
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

  /* ---------- 右侧入口（消息中心无对应演示页，与真实应用一致保持不可跳转） ---------- */
  function rightIconsHtml() {
    let html = '<div class="topbar__icons">';
    html += '<a class="icon-btn" href="' + href('my-workspaces') + '">' + icon('folder-open') + '<span>我的空间</span></a>';
    if (mode === 'project') {
      html += '<a class="icon-btn icon-btn--active" href="' + href('my-projects') + '">' + icon('folder') + '<span>我的项目</span></a>';
    }
    if (mode === 'workspace' || mode === 'project') {
      html += '<a class="icon-btn" href="' + href('workspace-manage') + '">' + icon('settings') + '<span>空间管理</span></a>';
    }
    const adminActive = shell === 'admin';
    html +=
      '<a class="icon-btn' +
      (adminActive ? ' icon-btn--active' : '') +
      '" href="' +
      href('system-admin') +
      '">' +
      icon('monitor') +
      '<span>系统管理</span></a>';
    html += '<div class="icon-btn"><span class="dot-badge">' + icon('bell') + '</span><span>消息中心</span></div>';
    html += '<span class="shell-divider"></span>';
    html +=
      '<div class="topbar__user"><span class="avatar avatar--brand">张</span>' +
      '<span class="topbar__username">张明</span>' +
      icon('chevron-down') +
      '</div>';
    return html + '</div>';
  }

  /* ---------- 顶栏（业务端 logo 可点回上下文首页，与 BusinessLayout.goHome 对齐） ---------- */
  function logoHtml() {
    const inner =
      '<span class="topbar__logo-mark">' + icon('zap') + '</span>' +
      '<span class="topbar__logo-text">RoboTest</span>';
    if (shell === 'admin') {
      return '<div class="topbar__logo">' + inner + '</div>';
    }
    const homeKey = mode === 'project' ? 'project-home' : 'my-workspaces';
    return '<a class="topbar__logo" href="' + href(homeKey) + '">' + inner + '</a>';
  }

  const topbar = '<header class="topbar">' + logoHtml() + tagsHtml() + menuHtml() + rightIconsHtml() + '</header>';

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
          '<a class="menu-item side__item' +
          (active === it.key ? ' menu-item--active' : '') +
          '" href="' +
          href(it.key) +
          '">' +
          icon(it.icon) +
          '<span>' +
          it.label +
          '</span></a>';
      });
    });
    return html + '</aside>';
  }

  /* ---------- project 页左侧导航（页面只留 data-side 占位；only 标记仅特定页展示的入口） ---------- */
  const SIDES = {
    // 顺序与标签同实际 useFunctionalTesting.menuItems：需求列表为侧栏首位子项（链路上游 需求→用例→缺陷，交互 07 §1）
    ft: [
      { key: 'ft-requirements', label: '需求列表', icon: 'tickets' },
      { key: 'ft-cases', label: '测试用例', icon: 'document' },
      { key: 'ft-reviews', label: '测试评审', icon: 'check-circle' },
      { key: 'ft-plans', label: '测试计划', icon: 'calendar' },
    ],
    api: [
      { key: 'api-debug', label: '快速调试', icon: 'terminal' },
      { key: 'api-interfaces', label: '接口管理', icon: 'link' },
      { key: 'api-mocks', label: 'Mock 服务', icon: 'cpu' },
      { key: 'api-scenes', label: '测试场景', icon: 'operation' },
      { key: 'api-reports', label: '测试报告', icon: 'data-chart' },
      { key: 'api-schedules', label: '定时任务', icon: 'timer' },
      { group: '项目设置' },
      { key: 'api-environments', label: '环境管理', icon: 'compass' },
      // GitLab 集成尚为设计稿（index.html 标注未实现），仅该设计稿页展示入口，其余接口页保持原视觉基准
      { key: 'api-gitlab', label: 'GitLab 集成', icon: 'connection', only: 'gitlab' },
      { key: 'api-functions', label: '函数管理', icon: 'layers' },
      { key: 'api-components', label: '公共组件', icon: 'box' },
    ],
  };

  // 详情/表单页归属其列表菜单项：页面文件名 → 应高亮的侧栏目标
  const SIDE_ACTIVE_ALIAS = {
    'api-schedule-form.html': 'api-schedules.html',
  };

  function currentFile() {
    const path = decodeURIComponent(location.pathname);
    const name = path.slice(path.lastIndexOf('/') + 1);
    return SIDE_ACTIVE_ALIAS[name] || name;
  }

  function sideItemHtml(it, include, current) {
    if (it.group) {
      return '<div class="side__group-title">' + it.group + '</div>';
    }
    if (it.only && include.indexOf(it.only) === -1) {
      return '';
    }
    const isCurrent = href(it.key).endsWith('/' + current);
    return (
      '<a class="menu-item side__item' +
      (isCurrent ? ' menu-item--active' : '') +
      '" href="' +
      href(it.key) +
      '">' +
      icon(it.icon) +
      '<span>' +
      it.label +
      '</span></a>'
    );
  }

  function renderSides() {
    document.querySelectorAll('.split__side[data-side]').forEach(function (el) {
      const def = SIDES[el.getAttribute('data-side')];
      if (!def) return;
      const include = (el.getAttribute('data-side-include') || '')
        .split(',')
        .map(function (s) { return s.trim(); })
        .filter(Boolean);
      const current = currentFile();
      el.innerHTML = def
        .map(function (it) { return sideItemHtml(it, include, current); })
        .join('');
    });

    // 兜底：个别页面若保留手写侧栏标记，按文案接上同一注册表，保证导航一律可跳转
    const labelKeys = {};
    Object.keys(SIDES).forEach(function (name) {
      SIDES[name].forEach(function (it) {
        if (it.key) labelKeys[it.label] = it.key;
      });
    });
    document.querySelectorAll('.split__side .menu-item').forEach(function (el) {
      if (el.tagName === 'A') return;
      const key = labelKeys[el.textContent.trim()];
      if (!key) return;
      const a = document.createElement('a');
      a.className = el.className;
      a.href = href(key);
      a.innerHTML = el.innerHTML;
      el.replaceWith(a);
    });
  }

  body.insertAdjacentHTML('afterbegin', topbar + sideHtml());
  renderSides();
  window.RTIcons.hydrate(document);
})();
