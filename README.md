<p align="center">
  <img src="https://img.shields.io/github/last-commit/SilinThoakes/DraCaveTitle?logo=artstation&style=for-the-badge&color=9266CC" />
  <img src="https://img.shields.io/github/issues/SilinThoakes/DraCaveTitle?style=for-the-badge&logo=slashdot" />
  <img src="https://img.shields.io/github/release/SilinThoakes/DraCaveTitle?style=for-the-badge&color=00C58E&logo=ionic" />
</p>

### 🔗 Links

-   📖 教程: [MineBBS 帖子](https://www.minebbs.com/threads/dracavetitle-gui.48962/#post-229305)
-   💬 测试群: `1101907336`

### 🎯 Introduce

DraCaveTitle 是一款面向 Paper 系服务端的动态称号插件，把「称号仓库 + 称号商店 + 自定义称号 + 管理面板」缝进了一个轻量插件里。

-   **真·动态称号**：支持渐变、彩虹、闪烁、帧动画，无需资源包
-   **可视化编辑**：管理员直接使用 GUI 面板自定义，开箱即用
-   **多货币商店**：支持 Vault / PlayerPoints / 称号币 / 自定义物品 (IA/CE)
-   **称号增值**：可挂载粒子特效、药水效果，内置数量排行榜
-   **跨服支持**：SQLite & MySQL 双存储，一键转换

### ✨ 五种动态动画类型

-   单字渐变（文本内色带流动）
-   整体变色（整段同色循环）
-   彩虹动态
-   多色交替闪烁
-   多帧文本轮播

> 渐变支持单向循环 / 回弹两种流动模式，可在单个称号内覆盖动画周期、渐变循环时间、渐变粒度，兼顾丝滑与发包体积。

### 🚀 Quick Start

1.  确保已安装前置 PlaceholderAPI 及 Tab 显示插件
2.  在对应位置使用变量符号放置称号
3.  加载插件后需管理员手动上传称号列表（可通过管理 GUI 直接上传）
4.  编辑 `titles.yml` 跟随注释自定义，或直接在 GUI 管理面板中修改

### ⌨️ Commands

| 命令 | 说明 |
| :--- | :--- |
| `/dctitle help` | 查看帮助 |
| `/dctitle open` | 打开称号仓库 |
| `/dctitle shop` | 打开称号商店 |
| `/dctitle listtitle` | 列出所有称号 |
| `/dctitle wear <ID>` | 穿戴称号 |
| `/dctitle wear none` / `clear` | 卸下称号 |
| `/dctitle custom` | 打开自定义称号 GUI |
| `/dctitle custom <名称>` | 快速创建静态自定义称号 |
| `/dctitle custom create/edit/delete` | 创建/编辑/删除自定义称号 |
| `/dctitle view [玩家]` | 查看自己/他人称号列表 |
| `/dctitle reward` | 打开奖励中心 |
| `/dctitle ranking` | 称号数量排行榜 |
| `/dctitle add/del/set/addPlayerTitle` | 称号增删改查 & 发放 |
| `/dctitle setDescription/addPermission` | 设置描述 & 购买权限 |
| `/dctitle setTitleBuff/delBuff` | 添加/移除药水效果 |
| `/dctitle setTitleParticle/removeTitleParticle` | 设置/移除粒子特效 |
| `/dctitle addCoin/subtractCoin` | 增加/扣除称号币 |
| `/dctitle setCustom/addCustom` | 设置/追加自定义额度 |
| `/dctitle addReward` | 配置里程碑奖励 |
| `/dctitle randomCard` | 生成随机称号卡 |
| `/dctitle changeItem` | 称号转物品卡 |
| `/dctitle adminShop` | 称号管理商店 GUI |
| `/dctitle panel/panel-id/panel-edit` | 管理面板搜索/编辑 |
| `/dctitle upload all/data/--check` | 上传/同步/校验 titles.yml |
| `/dctitle convert <MYSQL\|SQLITE>` | 转换存储类型 |
| `/dctitle reload` | 重载配置 |

### 🔐 Permissions

| 权限节点 | 说明 | 默认 |
| :--- | :--- | :--- |
| `dracave.title.use` | 玩家基础权限 | 全员 |
| `dracave.title.admin` | 管理员命令 | OP |
| `dracave.title.admin.panel` | 管理面板 | OP |
| `dracave.title.admin.upload` | 上传 titles.yml | OP |
| `dracave.title.custom.static` | 创建静态自定义称号 | 无 |
| `dracave.title.custom.dynamic` | 创建动态自定义称号 | 无 |
| `dracave.title.custom.limit.<n>` | 自定义上限 n 个 | 无 |
| `dracave.title.custom.limit.unlimited` | 自定义无限额度 | OP |
| `dracave.title.*` | 全部权限 | OP |
| `ttt.use` | PlayerTitle 数据迁移 | OP |

### 📊 Placeholders

| 变量 | 说明 |
| :--- | :--- |
| `%dracavetitle_title%` | 当前称号 (MiniMessage) |
| `%dracavetitle_title_legacy%` | 当前称号 (& 颜色码) |
| `%dracavetitle_title_legacy_section%` | 当前称号 (§ 颜色码) |
| `%dracavetitle_title_plain%` | 当前称号 (纯文本) |
| `%dracavetitle_title_id%` | 当前称号 ID |
| `%dracavetitle_has_title%` | 是否穿戴 (true/false) |
| `%dracavetitle_coin%` | 称号币余额 |

### 📊 bStats

<p align="center">
  <img src="https://bstats.org/signatures/bukkit/DraCaveTitle.svg" />
</p>

### 🚩 License

尽管 DraCaveTitle 是免费开源插件，你可以自由下载、编译、使用。
但如果你愿意通过赞助支持项目持续开发，我将非常感谢。
