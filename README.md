# DeltaForceStrike Client UI

**友谊之约：反制行动 · 客户端增强模块**  
非强制安装，为您提供更直观的对局信息与沉浸式音乐盒体验。

![Minecraft](https://img.shields.io/badge/Minecraft-26.2%2B-blue)
![Fabric](https://img.shields.io/badge/Fabric-0.19.3-orange)  


---

## 📖 简介

**DeltaForceStrike Client UI** 是专为 **友谊之约：反制行动** 服务器配套开发的客户端 MOD（非强制）。  
它在不影响原版游戏机制的前提下，为玩家提供：

- **实时战局 HUD**：计时器、比分、队伍血量、炸弹携带者标识；
- **音乐盒系统**：自定义回合背景音乐，增强竞技氛围；
- **轻量化设计**：资源占用低，兼容主流 MOD 加载器。

> 本 MOD 仅增强显示，不修改任何服务端逻辑，安装与否完全取决于个人喜好。

---

## ✨ 功能特性

### 🖥 战局信息 HUD
- 屏幕上方显示：
    - 当前回合剩余时间（精确到秒）；
    - T / CT 双方比分；
    - 双方队伍总血量（百分比或数值）；
    - 炸弹携带者头像/名称（带 💣 标记）。
- 支持位置、字体大小自定义（通过配置文件）。

### 🎵 音乐盒系统
- 每回合开始、胜利、失败、安包、拆包等关键事件可播放独立音轨；
- 内置《躺平青年》主题音乐包（默认启用）；
- 支持玩家自行添加 `.ogg` 或 `.wav` 格式音乐，通过 JSON 配置映射事件；
- 音乐播放独立于游戏音量，可独立开关。

### 🔧 其它辅助
- 低血量警告闪烁提示；
- 炸弹倒计时进度条；
- 队伍经济信息简表（可选）。

---

## 📦 安装

### 环境要求
| 项目        | 要求                              |
|-----------|---------------------------------|
| Minecraft | 26.2及以上                         |
| MOD 加载器   | Fabric                          |
| 服务端       | 需 DeltaForceStrike 服务端插件（v1.0+） |
| 客户端       | 无需额外依赖                          |

### 安装步骤
1. 从发布页下载对应加载器的 `.jar` 文件；
2. 放入 `.minecraft/mods/` 文件夹；
3. 启动游戏，进入 **友谊之约：反制行动** 服务器即可自动激活。

> 💡 **提示**：若未安装 MOD，服务端功能不受任何影响；若已安装，HUD 将在您加入对局后自动显示。

---

## ⚙️ 配置

配置文件位于 `.minecraft/config/deltaforcestrikeclientui/` 目录下：

- `clientui.json` – HUD 外观、位置、启用开关；
- `musicbox.json` – 音乐盒事件映射与音量设置（详见下文）。

首次运行 MOD 会自动生成默认配置。

### HUD 配置示例（`clientui.json`）
```json
{
  "masterVolume": 0.3,
  "music": {
    "waiting": {
      "file": "config/DeltaForceStrikeClient/mainmenu.wav"
    },
    "buy-1": {
      "file": "config/DeltaForceStrikeClient/buy1.wav"
    },
    "buy-2": {
      "file": "config/DeltaForceStrikeClient/buy2.wav"
    },
    "buy-3": {
      "file": "config/DeltaForceStrikeClient/buy3.wav"
    },
    "round-start-1": {
      "file": "config/DeltaForceStrikeClient/start1.wav"
    },
    "round-start-2": {
      "file": "config/DeltaForceStrikeClient/start2.wav"
    },
    "round-start-3": {
      "file": "config/DeltaForceStrikeClient/start3.wav"
    },
    "combat-10s": {
      "file": "config/DeltaForceStrikeClient/10remaining.wav"
    },
    "bomb-planted": {
      "file": "config/DeltaForceStrikeClient/bombplanted.wav"
    },
    "bomb-10s": {
      "file": "config/DeltaForceStrikeClient/10toexplode.wav"
    },
    "death": {
      "file": "config/DeltaForceStrikeClient/dead.wav"
    },
    "round-win": {
      "file": "config/DeltaForceStrikeClient/win.wav"
    },
    "round-lose": {
      "file": "config/DeltaForceStrikeClient/lose.wav"
    },
    "match-end": {
      "file": ""
    }
  }
}
```

---

## 🎵 音乐盒配置

音乐盒配置文件为 `config/deltaforcestrikeclientui.json`。  
您可以通过编辑此文件，将本地音乐文件绑定到不同游戏事件。

### 自定义音乐
1. 准备任意空目录；
2. 放入 `.ogg` 或 `.wav` 音频文件；
3. 修改 `musicbox.json` 中对应事件路径文件。

---

## 🎮 游戏内指令

| 指令                  | 说明      | 权限        |
|---------------------|---------|-----------|
| `/dfscui stopsound` | 强制停止音乐盒 | 无（客户端）  |
| `/dfsui reload`     | 重载配置文件  | 无（客户端）  |

所有指令均可在聊天栏输入，仅影响本地客户端。

## ❓ 常见问题

**Q：必须安装此 MOD 才能玩吗？**  
A：不需要。本 MOD 完全非强制，不装也能正常进行游戏。

**Q：为什么 HUD 没有显示？**  
A：请确认您已加入对局（非观战），且服务端插件版本支持。

**Q：音乐盒没声音？**  
A：检查 `deltaforcestrikeclientui.json` 中音量不为 0，并确保音频文件路径正确且格式兼容。

**Q：我可以自己制作音乐包并分享给朋友吗？**  
A：当然！只需将音乐文件夹与 `deltaforcestrikeclientui.json` 打包即可，配置文件使用相对路径，开箱即用。

**享受更沉浸的爆破竞技，我们在友谊之约等你！**