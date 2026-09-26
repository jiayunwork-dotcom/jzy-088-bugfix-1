# BEM 风轮叶素动量气动核算服务

小型水平轴风轮的**叶素动量（Blade Element Momentum）气动核算**微服务。
只做这一层计算并经 HTTP 输出 JSON，不含前端、不做登录。

输入一副叶片（沿半径切分的叶素：径向位置、弦长、扭角）、一张随攻角变化的
升/阻力系数表和叶尖速比，服务逐叶素迭代求轴向/切向诱导因子，给出当地攻角、
微元推力、微元扭矩，并沿半径积分出整机功率系数与推力系数。

技术栈：Java 17 · Spring Boot 3 · Maven 3.9 · Temurin JDK 17。

---

## 八个彼此独立、各自成文件的模块

| # | 模块 | 文件 | 职责 |
|---|------|------|------|
| 1 | 入流角与攻角 | `inflow/InflowGeometry.java` | `tanφ=(1-a)/((1+a')λμ)`、`α=φ-θ`，纯几何 |
| 2 | 翼型表插值 | `polar/PolarInterpolator.java` | 按攻角分段线性插 Cl/Cd，越界钳端点 |
| 3 | 诱导因子迭代与尾流修正 | `induction/InductionSolver.java` | 单叶素固定点迭代；小诱导用线性动量式，高诱导（a>a_c=1/3）切到 Buhl/Glauert 修正推力二次式；自适应欠松弛；不收敛抛异常 |
| 4 | 叶尖损失 | `tiploss/PrandtlTipLoss.java`、`tiploss/PrandtlHubLoss.java` | 普朗特叶尖/轮毂损失因子 F |
| 5 | 沿叶片积分 | `integration/RotorIntegrator.java` | 逐站调内核、算微元载荷、梯形积分得 Cp/Ct/Cq |
| 6 | 翼型数据登记管理 | `registry/AirfoilRegistry.java` | 具名极曲线内存登记，供多次分析引用 |
| 7 | 输入校验 | `validation/RequestValidator.java` | 合法性判定与稳定机器码 |
| 8 | HTTP 层 | `web/` | 控制器、DTO、统一错误处理；不写气动公式 |

领域对象在 `domain/`，跨模块编排（DTO↔领域、校验、解析翼型）在 `service/AnalysisService.java`，
内置算例在 `sample/`。迭代内核与积分逻辑分属 `InductionSolver` 与 `RotorIntegrator` 两个类，
没有堆进同一个大类。

### 气动模型要点

- 速度三角形（以远前方风速 U 无量纲化）：轴向 `1-a`，切向 `(1+a')λμ`。
- 法向/切向力系数：`Cn=Cl cosφ+Cd sinφ`，`Ct=Cl sinφ-Cd cosφ`；当地实度 `σ=Bc/(2πr)`。
- 小诱导：`a=f/(F+f)`，`f=σCn/(4sin²φ)`；切向 `a'=σCt/(4F cosφ-σCt)`。
- 湍流尾流（线性解超过 a_c=1/3）：改用 Buhl(2005) 修正推力关系
  `CT=γ2a²+γ1a+γ0`（在 a_c 处值与斜率连续、a=1 时 CT=1；F=1 退化为经典
  Glauert 曲线 `CT=-(11/9)a²+(16/9)a+4/9`），解二次方程取物理根，
  **不再硬套小诱导公式把分母逼失稳**。分支按“当前轮线性解”判定，并配合
  自适应欠松弛抑制临界点极限环。
- 普朗特损失：`F=F_tip·F_hub`，可分别开关。
- 微元载荷（环面定义）：`dCt/dμ=(Bc/π)(1-a)²Cn/sin²φ`，
  `dCq/dμ=(Bc/π)μ(1-a)²Ct/sin²φ`；梯形积分 `Cp=λ·Cq`。
- 收敛容差 1e-6、上限 400 轮；越界、NaN、找不到物理根或超时一律抛
  `NonConvergentException`，HTTP 422，绝不返回发散结果。

---

## HTTP 接口

只有两个分析能力，外加翼型登记管理与内置算例查看。错误统一返回
`{"code": 机器码, "message": 可读说明}`。

### 1. 单叶素 + 叶尖速比
`POST /api/analyze/station?stationIndex=4`（或 `?mu=0.6`，缺省取叶中站）
返回该站 `axialInduction`、`tangentialInduction` 及在此几何下整机积分的
`powerCoefficient`、`thrustCoefficient`。

### 2. 整片叶片叶素序列
`POST /api/analyze/rotor`
返回每个半径处 `angleOfAttackDeg`、`dCt`（微元推力）、`dCq`（微元扭矩）
及整机 `powerCoefficient`、`thrustCoefficient`、`torqueCoefficient`。

请求体：
```json
{
  "tipSpeedRatio": 6.0,
  "bladeCount": 3,
  "elements": [
    {"mu": 0.25, "chord": 0.2277, "twist": 17.96}
  ],
  "polarName": "NACA4412-SAMPLE",
  "useTipLoss": true,
  "useHubLoss": true,
  "hubRadius": 0.20
}
```
`polar`（内联表 `{name, alphas[], cl[], cd[]}`）与 `polarName`（引用登记翼型）
二选一。角度一律为度；`mu=r/R`、`chord=c/R` 无量纲。

### 翼型登记管理
- `GET /api/polars`、`GET /api/polars/{name}`
- `POST /api/polars`（body 为内联极曲线，同名覆盖）
- `DELETE /api/polars/{name}`

### 内置三叶片算例
`GET /api/samples/rotor` 直接返回可用于上面两个接口的请求体（9 个径向站、
λ=6、B=3、引用已登记示例翼型）。

设计点核算结果（可手工核对）：

| 量 | 值 |
|----|----|
| 功率系数 Cp | **≈ 0.417**（正，< 贝兹极限 16/27≈0.593） |
| 推力系数 Ct | ≈ 0.719 |
| 叶中（μ=0.6）a / a' | ≈ 0.336 / 0.0029，攻角 ≈ 5.93° |
| 梢部（μ=0.95）F | ≈ 0.72 |
| 叶尖损失关闭后梢部 dCt | 1.42 → 1.69（升高） |
| 叶片数 3→2 后 Cp | 0.417 → 0.368（下降） |

### 非法输入机器码

| code | 触发条件 |
|------|----------|
| `TSR_NOT_POSITIVE` | 叶尖速比不为正 |
| `BLADE_COUNT_NOT_POSITIVE` | 叶片数不为正整数 |
| `ELEMENTS_TOO_FEW` | 径向站点少于 3 个 |
| `MU_NOT_MONOTONIC` | 径向坐标非严格单调 |
| `CHORD_NOT_POSITIVE` / `MU_OUT_OF_RANGE` | 弦长/径向位置越界 |
| `POLAR_EMPTY` / `POLAR_TOO_FEW_POINTS` / `POLAR_ALPHA_NOT_MONOTONIC` 等 | 翼型表问题 |
| `POLAR_NOT_FOUND` (404) | 引用了未登记翼型 |
| `MALFORMED_JSON` | 请求体非合法 JSON |
| `NOT_CONVERGED` (422) | 诱导因子迭代不收敛 |

---

## 构建、测试与运行

```bash
# 本地（Maven 3.9 + JDK 17）
mvn test            # 38 个测试
mvn spring-boot:run # http://localhost:8080

# 容器：单容器对外
docker build -t bem-rotor-service:1.0.0 .
docker run --rm -p 8080:8080 bem-rotor-service:1.0.0
# 或
docker compose up --build
```

快速试用：
```bash
curl -s localhost:8080/api/samples/rotor \
  | curl -s -X POST localhost:8080/api/analyze/rotor \
      -H 'Content-Type: application/json' --data @-
```

---

## 测试锁定的气动规律（均带容差）

- 收敛后叶中区（0.3≤μ≤0.8）轴向诱导因子落在 [0, 1/2]；
- Cp 为正且严格小于贝兹极限 16/27；
- λ 从 4→6 抬向设计点时 Cp 显著上升，6→8 趋于平台而非单调乱跳；
- 关掉叶尖损失后梢部微元推力升高（且 F 恒为 1）；
- 其它不变、叶片数 3→2，Cp 明显下降；
- 极端实度工况不收敛时抛异常而非返回发散值；
- 五类非法输入各自返回正确机器码；两个 HTTP 能力与翼型登记生命周期端到端验证。

测试见 `src/test/java/com/example/bem/`。
