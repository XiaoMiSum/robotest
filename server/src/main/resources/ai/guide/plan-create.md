---
topic: [创建计划, 测试计划, 计划执行, plan, 如何创建计划]
route: /workspace/projects/plans
roles: []
---

## 如何创建测试计划

1. 进入「功能测试 → 计划」页（路由 `/workspace/projects/plans`）。
2. 点击「创建计划」，填写计划名称、选择执行环境、负责人，并在用例规划中选择要执行的用例文档。
3. 创建后计划状态为「未开始」（new），系统生成用例快照。
4. 负责人开始执行后状态转为「进行中」（in_progress）；执行中可对每个用例节点提交执行记录（通过/失败/阻塞）。
5. 全部用例执行完成后可「完成计划」（completed）；已完成的计划可「关闭」（closed）。

## 状态流转

new → in_progress → completed → closed；关闭后不可再执行。
