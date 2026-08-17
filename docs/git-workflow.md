# Git 分支与 Pull Request 协作规范

本规范适用于 PlantProtectionDroneVehicle 仓库。当前仓库为 GitHub 免费个人私有仓库，平台无法强制保护 `master`，因此所有协作者必须共同遵守以下流程。

## 1. 核心规则

1. `master` 是唯一长期分支，必须始终保持可构建、可运行。
2. 禁止协作者直接向 `master` 推送代码，包括执行 `git push origin master`。
3. 禁止对 `master` 强制推送、删除或改写历史。
4. 每项功能、修复或文档修改都必须从最新 `master` 创建独立分支。
5. 所有代码必须通过 Pull Request（PR）进入 `master`。
6. 仓库所有者 `xindong1314` 负责最终审核和合并。
7. 一个 PR 只处理一个明确主题，不混入无关格式化或功能修改。
8. PR 合并后删除对应功能分支；下一项任务重新从最新 `master` 创建分支。

## 2. 分支命名

统一使用“小写类型/用户名-简短主题”：

| 类型 | 用途 | 示例 |
|---|---|---|
| `feat/` | 新功能 | `feat/zhang-device-map` |
| `fix/` | 缺陷修复 | `fix/li-login-timeout` |
| `docs/` | 文档修改 | `docs/wang-deployment` |
| `refactor/` | 不改变功能的重构 | `refactor/zhao-mqtt-service` |
| `chore/` | 构建、依赖和工具调整 | `chore/chen-vite-config` |

不得使用姓名本身、`test`、`new`、`dev` 等无法说明用途的分支名。

## 3. 开始开发

先确认当前工作区没有未提交修改：

```bash
git status
```

同步主分支并创建自己的功能分支：

```bash
git switch master
git pull --ff-only origin master
git switch -c feat/你的用户名-功能名称
```

如果 `git status` 显示存在不属于本次任务的修改，不要删除或覆盖，先联系仓库所有者处理。

## 4. 提交与推送

提交前检查变更范围：

```bash
git status
git diff
```

只暂存本次修改的文件，避免直接使用无法确认范围的批量操作：

```bash
git add 具体文件路径
git commit -m "feat: 增加设备地图"
```

常用提交类型为 `feat`、`fix`、`docs`、`refactor`、`test`、`chore`。提交信息应说明做了什么，不使用“修改”“完成”“update”等模糊描述。

首次推送自己的分支：

```bash
git push -u origin feat/你的用户名-功能名称
```

后续修改继续提交并执行 `git push`，原 PR 会自动更新，不要重复创建 PR。

## 5. 创建 PR

在 GitHub 创建 PR 时必须确认：

- Base 分支是 `master`。
- Compare 分支是自己的功能分支。
- 标题能概括修改，例如 `feat(PPDVweb): 增加设备地图`。
- 描述包含修改内容、影响范围、验证命令和验证结果。
- 没有提交密码、Token、私钥、真实生产配置或个人隐私数据。
- 没有删除与本任务无关的文件和功能。

推荐 PR 描述：

```markdown
## 修改内容
- 

## 影响范围
- 

## 验证结果
- [ ] 后端测试通过
- [ ] 前端构建通过
- [ ] 已完成必要的手工验证
```

## 6. 同步审核期间的主分支更新

如果其他 PR 已先合并，先把最新 `master` 合入自己的功能分支：

```bash
git switch 你的功能分支
git fetch origin
git merge origin/master
```

解决冲突后重新构建和测试，再提交并推送：

```bash
git add 发生冲突的具体文件
git commit
git push
```

不熟悉冲突处理时立即停止操作并联系仓库所有者，不得通过删除文件、重置分支或强制推送来绕过冲突。

## 7. 审核与合并条件

仓库所有者在合并前至少确认：

- 修改目的和范围清晰，没有无关改动。
- 不会覆盖或删除 `master` 已有功能。
- 接口、数据库迁移和配置保持兼容。
- 后端执行了相应 Maven 测试。
- 前端执行了 `npm ci` 和 `npm run build`。
- 安全相关改动没有引入越权、路径穿越、敏感信息泄露等问题。
- 所有审核意见均已处理。

不满足条件的 PR 不合并，由提交人在原功能分支继续修改。

## 8. 合并后的清理

PR 合并后，在 GitHub 删除远端功能分支。本地执行：

```bash
git switch master
git pull --ff-only origin master
git branch -d 你的功能分支
git fetch --prune
```

如果 `git branch -d` 拒绝删除，不要改用 `-D`，先确认 PR 是否确实合并。

## 9. 禁止操作

未经仓库所有者明确确认，禁止执行：

```bash
git push origin master
git push --force
git push --force-with-lease
git reset --hard
git branch -D
```

也禁止直接修改他人的功能分支、复用已经合并的旧分支，或将生成目录、IDE 缓存和本地密钥提交到仓库。

## 10. 误操作处理

如果误向 `master` 推送、误删文件或产生异常合并：

1. 立即停止后续 Git 操作。
2. 保存终端输出和相关提交哈希。
3. 通知仓库所有者说明执行过的命令。
4. 不自行 reset、rebase、force-push 或删除分支。

优先通过新的回滚提交恢复，避免改写共享历史。

