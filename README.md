<div align="center">

# 🏷️ DraCaveTitle

**面向 Paper 系服务端的动态称号插件**

[![QQ群](https://img.shields.io/badge/QQ群-1101907336-blue?style=flat-square&logo=tencentqq)](https://qm.qq.com/cgi-bin/qm/qr?k=&jump_from=webapi&authKey=&group_code=1101907336)
[![教程](https://img.shields.io/badge/使用教程-MineBBS-orange?style=flat-square&logo=bookstack)](https://www.minebbs.com/threads/dracavetitle-gui.48962/#post-229305)

</div>

---

DraCaveTitle 是一款面向 Paper 系服务端的动态称号插件，把「 **称号仓库 + 称号商店 + 自定义称号 + 管理面板** 」缝进了一个轻量的插件里。

称号支持渐变、彩虹、闪烁、帧动画等多种动态效果，可挂载粒子特效与药水效果，并能通过 **Vault / PlayerPoints / 称号币 / 物品**（包括 IA 和 CE 等自定义物品）四种货币在商店出售，是公益服、商业服、RPG 服、小游戏服都好用的称号解决方案。

## ✨ 核心亮点

| 特性 | 说明 |
| :--- | :--- |
| 🎨 **真·动态称号** | 无需资源包，纯服务端实现 |
| 📦 **一体化管理** | 开箱即用，无需额外依赖 |
| 🖥️ **可视化编辑** | 管理员直接使用 GUI 面板自定义 |
| 🌟 **称号增值** | 粒子效果、状态效果、数量排行榜 |
| 🌐 **跨服支持** | SQLite 与 MySQL 双存储 |

### 🎬 五种动态动画类型

- **单字渐变** — 文本内色带流动
- **整体变色** — 整段同色循环
- **🌈 彩虹动态** — 经典彩虹流光
- **💡 多色交替闪烁** — 多色呼吸灯效果
- **🎞️ 多帧文本轮播** — 文字逐帧切换

> 💡 渐变支持**单向循环**与**回弹**两种流动模式，可在单个称号内覆盖动画周期、渐变循环时间、渐变粒度，兼顾丝滑与发包体积。

---

## 🚀 如何使用？

1. 确保你有前置的 **PlaceholderAPI** 以及 **Tab** 类插件
2. 使用变量符号，放置称号所在的位置
3. 加载插件后插件并不加载称号列表，需管理员手动上传（管理 GUI 可直接上传）

## 🛠️ 如何自定义？

1. 进入插件自动生成的文件夹，寻找 `titles.yml`
2. 跟随注释教程，参考下列称号进行自定义，支持动态颜色、渐变方向、称号类型

> 💡 如果你不想这么麻烦，也提供了 **GUI 管理员界面**，全都可以直接修改！

---

## 📜 命令一览

### 👤 玩家命令

| 命令 | 说明 |
| :--- | :--- |
| `/dctitle help` | 查看帮助 |
| `/dctitle open` | 打开称号仓库 |
| `/dctitle shop` | 打开称号商店 |
| `/dctitle listtitle` | 列出所有称号 |
| `/dctitle wear <ID>` | 穿戴称号 |
| `/dctitle wear none` | 卸下称号 |
| `/dctitle clear` | 卸下称号 |
| `/dctitle custom` | 打开自定义称号 GUI |
| `/dctitle custom <名称>` | 快速创建静态自定义称号 |
| `/dctitle custom create <类型> <参数>` | 创建自定义称号 |
| `/dctitle custom edit <ID> <类型> <参数>` | 编辑自定义称号 |
| `/dctitle custom delete <ID>` | 删除自定义称号 |
| `/dctitle view` | 查看自己称号列表 |
| `/dctitle view <玩家>` | 查看他人称号列表 |
| `/dctitle reward` | 打开奖励中心 |
| `/dctitle ranking` | 称号数量排行榜 |

### ⚙️ 管理员命令

| 命令 | 说明 |
| :--- | :--- |
| `/dctitle add <货币> <名称> <价格> [天数] [隐藏] [玩家]` | 创建称号 |
| `/dctitle del <ID>` | 删除称号 |
| `/dctitle set <玩家> <ID> [天数]` | 设置并强制穿戴 |
| `/dctitle addPlayerTitle <玩家> <ID> [天数]` | 发放称号 |
| `/dctitle setDescription <ID> <描述>` | 设置描述 |
| `/dctitle addPermission <ID> <权限>` | 设置购买权限 |
| `/dctitle setTitleBuff <ID> POTION_EFFECT <效果> [等级]` | 添加药水 |
| `/dctitle delBuff <ID> <效果>` | 移除药水 |
| `/dctitle setTitleParticle <ID> <粒子> [id] [颜色...]` | 设置粒子 |
| `/dctitle removeTitleParticle <ID>` | 移除粒子 |
| `/dctitle addCoin <玩家> <金额>` | 增加称号币 |
| `/dctitle subtractCoin <玩家> <金额>` | 扣除称号币 |
| `/dctitle setCustom <玩家> <次数>` | 设置自定义额度 |
| `/dctitle addCustom <玩家> <次数>` | 追加自定义额度 |
| `/dctitle addReward <数量> <货币> <金额>` | 配置里程碑奖励 |
| `/dctitle randomCard <货币> <天数>` | 生成随机称号卡 |
| `/dctitle changeItem <ID> <天数> <数量> [玩家]` | 称号转物品卡 |
| `/dctitle adminShop` | 称号管理商店 GUI |
| `/dctitle panel` | 按名称搜索打开管理面板 |
| `/dctitle panel-id <ID>` | 按 ID 打开管理面板 |
| `/dctitle panel-edit <ID> text <新文本>` | 命令行改文本 |
| `/dctitle panel-edit <ID> price <金额\|none>` | 命令行改价格 |
| `/dctitle upload all` | 上传 titles.yml 到数据库 |
| `/dctitle upload data` | 从数据库同步到 titles.yml |
| `/dctitle upload all --check` | 仅校验 titles.yml |
| `/dctitle convert <MYSQL\|SQLITE>` | 转换存储类型 |
| `/dctitle reload` | 重载配置 |

---

## 🔑 权限一览

| 权限节点 | 说明 | 默认 |
| :--- | :--- | :--- |
| `dracave.title.use` | 玩家基础权限 | ✅ 全员 |
| `dracave.title.admin` | 管理员命令 | 🔒 OP |
| `dracave.title.admin.panel` | 管理面板 | 🔒 OP |
| `dracave.title.admin.upload` | 上传 titles.yml | 🔒 OP |
| `dracave.title.custom.static` | 创建静态自定义称号 | ❌ 无 |
| `dracave.title.custom.dynamic` | 创建动态自定义称号 | ❌ 无 |
| `dracave.title.custom.limit.1` | 自定义上限 x 个 | ❌ 无 |
| `dracave.title.custom.limit.unlimited` | 自定义无限额度 | 🔒 OP |
| `dracave.title.*` | 全部权限 | 🔒 OP |
| `ttt.use` | PlayerTitle 数据迁移 | 🔒 OP |

---

## 📊 变量一览 (PlaceholderAPI)

| 变量 | 说明 |
| :--- | :--- |
| `%dracavetitle_title%` | 当前称号（MiniMessage 格式） |
| `%dracavetitle_title_legacy%` | 当前称号（& 颜色码格式） |
| `%dracavetitle_title_legacy_section%` | 当前称号（§ 颜色码格式） |
| `%dracavetitle_title_plain%` | 当前称号（纯文本） |
| `%dracavetitle_title_id%` | 当前称号 ID |
| `%dracavetitle_has_title%` | 是否穿戴（true/false） |
| `%dracavetitle_coin%` | 称号币余额 |
