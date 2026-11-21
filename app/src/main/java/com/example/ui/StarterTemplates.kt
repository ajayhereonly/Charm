package com.example.ui

import com.example.firebase.ScriptCategory
import com.example.firebase.TriggerMode

data class StarterScriptSeed(
    val filename: String,
    val folder: String,
    val category: ScriptCategory,
    val description: String,
    val content: String
)

data class StarterAppSeed(
    val name: String,
    val summary: String,
    val iconKey: String,
    val accentHex: String,
    val triggerMode: TriggerMode,
    val intervalSeconds: Int,
    val has3dViewport: Boolean,
    val inputsSpec: String,
    val pythonCode: String
)

object StarterTemplates {

    val scripts: List<StarterScriptSeed> = listOf(
        StarterScriptSeed(
            filename = "orbital_gyro_3d.py",
            folder = "models_3d",
            category = ScriptCategory.MODEL_3D,
            description = "Procedural GPU 3D Orbital Gyroscope using py3d & math",
            content = """
import math
import py3d

scene = py3d.Scene("Orbital Gyroscope")

# Central geodesic crystal core
scene.add_icosahedron(radius=0.85, pos=(0, 0.2, 0), color="#FFB300", metallic=0.75)

# Concentric gimbal rings
scene.add_torus(major=1.65, minor=0.12, rot=(28, 0, 18), pos=(0, 0.2, 0), color="#00E5FF")
scene.add_torus(major=1.30, minor=0.10, rot=(-35, 45, -20), pos=(0, 0.2, 0), color="#C792EA")

# Ring of orbital telemetry satellites
sat_count = 6
for i in range(sat_count):
    angle = 2 * math.pi * i / sat_count
    x = math.cos(angle) * 2.15
    z = math.sin(angle) * 2.15
    y = 0.2 + 0.35 * math.sin(angle * 2)
    scene.add_sphere(radius=0.18, segments=16, pos=(x, y, z), color="#22D3A0")

scene.render()
print("Built orbital gyroscope with", sat_count, "satellites.")
            """.trimIndent()
        ),
        StarterScriptSeed(
            filename = "wave_surface_3d.py",
            folder = "models_3d",
            category = ScriptCategory.MODEL_3D,
            description = "Parametric 3D Bessel-like harmonic wave surface",
            content = """
import math
import py3d

scene = py3d.Scene("Harmonic Wave Surface")

def ripple(x, z):
    r = math.sqrt(x * x + z * z) + 0.001
    return 1.15 * math.sin(r * 2.4) / (0.6 + r * 0.45)

scene.add_surface(ripple, x_range=(-2.6, 2.6), z_range=(-2.6, 2.6), steps=24, color="#00E5FF")
scene.add_sphere(radius=0.25, pos=(0, 1.35, 0), color="#FFB300")
scene.render()
print("Parametric 3D wave mesh generated and sent to OpenGL ES GPU.")
            """.trimIndent()
        ),
        StarterScriptSeed(
            filename = "data_science_lab.py",
            folder = "analytics",
            category = ScriptCategory.DATA_SCIENCE,
            description = "NumPy, Pandas, and Matplotlib interactive signal analysis",
            content = """
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

# Generate damped harmonic telemetry signal
t = np.linspace(0, 10, 48)
signal = np.exp(-0.18 * t) * np.sin(2.2 * t)
envelope = np.exp(-0.18 * t)

print(f"Samples: {len(t)} | Peak: {np.max(signal):.3f} | StdDev: {np.std(signal):.3f}")

df = pd.DataFrame({
    "metric": ["Velocity", "Torque", "Thermal", "Efficiency"],
    "score": [94.2, 88.5, 91.0, 96.4],
    "delta": [4.1, 2.8, -0.5, 6.2]
})
print("\nSystem Telemetry Summary:")
print(df)

plt.title("Damped Harmonic Oscillation")
plt.xlabel("Time (s)")
plt.ylabel("Amplitude")
plt.plot(t, signal, label="Signal", color="#00E5FF")
plt.plot(t, envelope, label="Envelope", color="#FFB300")
plt.grid(True)
plt.show()
            """.trimIndent()
        ),
        StarterScriptSeed(
            filename = "dna_helix_3d.py",
            folder = "models_3d",
            category = ScriptCategory.MODEL_3D,
            description = "Dual-strand 3D DNA double helix with base-pair rungs",
            content = """
import math
import py3d

scene = py3d.Scene("DNA Double Helix")
scene.add_helix(radius=0.95, height=3.4, turns=2.2, tube=0.11, color="#00E5FF")
scene.add_helix(radius=0.95, height=3.4, turns=2.2, tube=0.11, rot=(0, 180, 0), color="#FFB300")

for i in range(12):
    frac = i / 11.0
    y = (frac - 0.5) * 3.1
    ang = 2 * math.pi * 2.2 * frac
    deg = math.degrees(ang)
    col = "#22D3A0" if i % 2 == 0 else "#C792EA"
    scene.add_cylinder(radius=0.05, height=1.8, pos=(0, y, 0), rot=(0, -deg, 90), color=col)

scene.render()
print("DNA double-helix CAD model compiled for GPU rendering.")
            """.trimIndent()
        )
    )

    val automatedApps: List<StarterAppSeed> = listOf(
        StarterAppSeed(
            name = "3D Parametric Gear Lab",
            summary = "Auto-builds precision interlocking 3D CAD gears and torque metrics live as you slide teeth & ratio.",
            iconKey = "cube",
            accentHex = "#00E5FF",
            triggerMode = TriggerMode.ON_CHANGE,
            intervalSeconds = 0,
            has3dViewport = true,
            inputsSpec = """
slider|teeth|Drive Gear Teeth|14|8|24
slider|thickness|Gear Thickness|0.42|0.15|1.0
slider|rpm|Input RPM|180|30|600
toggle|dual|Add Interlocking Pinion|true
            """.trimIndent(),
            pythonCode = """
import math
import py3d

t_drive = int(teeth)
t_pinion = max(6, int(t_drive * 0.65))
ratio = t_drive / t_pinion
out_rpm = rpm * ratio
outer_r = 0.85 + t_drive * 0.035

scene = py3d.Scene("Parametric Gear Assembly")
scene.add_gear(teeth=t_drive, outer_radius=outer_r, inner_radius=outer_r * 0.78, thickness=thickness, pos=(-0.75 if dual else 0.0, 0.1, 0), color="#00E5FF")
scene.add_cylinder(radius=0.22, height=thickness + 0.35, pos=(-0.75 if dual else 0.0, 0.1, 0), color="#94A3B8")

if dual:
    pinion_r = outer_r * 0.65
    offset_x = outer_r + pinion_r - 0.85
    scene.add_gear(teeth=t_pinion, outer_radius=pinion_r, inner_radius=pinion_r * 0.76, thickness=thickness, pos=(offset_x, 0.1, 0), rot=(0, 15, 0), color="#FFB300")
    scene.add_cylinder(radius=0.16, height=thickness + 0.35, pos=(offset_x, 0.1, 0), color="#94A3B8")

scene.render()

print(f"METRIC|Gear Ratio|{ratio:.2f}:1|Drive/Pinion")
print(f"METRIC|Output Speed|{out_rpm:.0f}|RPM")
print(f"METRIC|Pitch Diameter|{outer_r * 20:.1f}|mm")
print(f"Automated CAD assembly updated: {t_drive}T drive gear + {t_pinion}T pinion.")
            """.trimIndent()
        ),
        StarterAppSeed(
            name = "Wealth & Yield Forecaster",
            summary = "Automated compound growth engine with real-time projection charts and inflation-adjusted metrics.",
            iconKey = "chart",
            accentHex = "#22D3A0",
            triggerMode = TriggerMode.ON_CHANGE,
            intervalSeconds = 0,
            has3dViewport = false,
            inputsSpec = """
slider|principal|Initial Capital ($)|10000|1000|100000
slider|monthly|Monthly Deposit ($)|500|0|5000
slider|rate|Annual Return (%)|8.5|1.0|20.0
slider|years|Horizon (Years)|15|1|40
            """.trimIndent(),
            pythonCode = """
import numpy as np
import matplotlib.pyplot as plt

yrs = int(years)
r = rate / 100.0 / 12.0
balances = []
contributed = []
bal = float(principal)
contrib = float(principal)

for y in range(yrs + 1):
    balances.append(bal)
    contributed.append(contrib)
    for _ in range(12):
        bal = bal * (1 + r) + monthly
        contrib += monthly

final_val = balances[-1]
total_in = contributed[-1]
profit = final_val - total_in
roi = (profit / max(total_in, 1)) * 100.0

print(f"METRIC|Projected Total|${'$'}{final_val:,.0f}|After {yrs} yrs")
print(f"METRIC|Net Gain|+${'$'}{profit:,.0f}|{roi:.1f}% ROI")
print(f"METRIC|Total Deposited|${'$'}{total_in:,.0f}|Principal + Monthly")

xs = list(range(yrs + 1))
plt.title("Automated Compound Growth Trajectory")
plt.xlabel("Years")
plt.ylabel("Portfolio Value ($)")
plt.plot(xs, balances, label="Total Value", color="#22D3A0")
plt.plot(xs, contributed, label="Deposited", color="#FFB300")
plt.show()
print(f"Forecast complete across {yrs * 12} compounding periods.")
            """.trimIndent()
        ),
        StarterAppSeed(
            name = "Live Harmonic Wave Lab",
            summary = "Interval-automated 3D physics wave engine that evolves smoothly on a timer or parameter change.",
            iconKey = "rocket",
            accentHex = "#C792EA",
            triggerMode = TriggerMode.INTERVAL,
            intervalSeconds = 2,
            has3dViewport = true,
            inputsSpec = """
slider|freq|Wave Frequency|2.2|0.8|5.0
slider|amp|Amplitude|1.0|0.2|2.0
            """.trimIndent(),
            pythonCode = """
import math
import py3d

phase = TICK * 0.65
scene = py3d.Scene("Live Harmonic Field")

def field(x, z):
    r = math.sqrt(x * x + z * z)
    return amp * math.sin(r * freq - phase) * math.exp(-0.14 * r)

scene.add_surface(field, x_range=(-2.4, 2.4), z_range=(-2.4, 2.4), steps=18, color="#00E5FF")
scene.add_sphere(radius=0.22, pos=(0, amp * math.sin(-phase) + 0.35, 0), color="#FFB300")
scene.render()

energy = 0.5 * amp * amp * freq * freq
print(f"METRIC|Wave Tick|#{TICK}|Auto-running")
print(f"METRIC|Peak Energy|{energy:.2f}|Joules")
print(f"METRIC|Phase Angle|{(math.degrees(phase) % 360):.0f}°|Radians: {phase:.2f}")
print(f"Tick #{TICK}: Recomputed 3D standing wave mesh.")
            """.trimIndent()
        )
    )
}
