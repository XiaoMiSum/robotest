---
topic: [缺陷流转, 缺陷状态, 解决缺陷, 关闭缺陷, bug status, 缺陷流程]
route: /workspace/projects/bugs/{bugId}
roles: []
---

## 缺陷状态流转规则

1. 从缺陷列表进入缺陷详情页（路由模板 `/workspace/projects/bugs/{bugId}`）。
2. 缺陷状态机（三态）：激活 active → 已解决 resolved → 已关闭 closed；解决后可「重开」回激活。

## 状态流转说明

- active → resolved：处理人提交解决方案（如代码修复），填写解决方案与说明。
- resolved → closed：报告人验证通过后关闭。
- active → rejected：确认非缺陷，填写拒绝理由。
- resolved → active 或 closed → active：验证不通过或问题复现时重开（重开计数递增）。

## 操作权限

- 确认缺陷、指派处理人：激活状态可执行。
- 解决/拒绝：当前处理人。
- 关闭/重开：报告人或管理员。
