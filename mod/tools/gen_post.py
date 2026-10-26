#!/usr/bin/env python3
"""Generates the post-processing chains (colour grade and motion blur variants) and per-version shaders.

Minecraft post effects take their uniform values from JSON, so the mod ships a fixed grid of variants and picks the
nearest one for the player's slider values. Run from the mod/ directory.
"""
import json
import os

SAT = [0.0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0]
CON = [0.7, 0.85, 1.0, 1.15, 1.3]
WARM = [-2.0, -1.0, 0.0, 1.0, 2.0]
BLUR = [0.1 * i for i in range(1, 10)]

OUT = "common/src/main/resources/assets/nocturnum/post_effect"
os.makedirs(OUT, exist_ok=True)


def blit(src, dst):
    return {
        "vertex_shader": "minecraft:core/screenquad",
        "fragment_shader": "minecraft:post/blit",
        "inputs": [{"sampler_name": "In", "target": src}],
        "uniforms": {"BlitConfig": [{"name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0]}]},
        "output": dst,
    }


def grade(s, c, w):
    return {
        "targets": {"swap": {}},
        "passes": [
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "nocturnum:post/grade",
                "inputs": [{"sampler_name": "In", "target": "minecraft:main"}],
                "output": "swap",
                "uniforms": {"GradeConfig": [
                    {"name": "Saturation", "type": "float", "value": s},
                    {"name": "Contrast", "type": "float", "value": c},
                    {"name": "Warmth", "type": "float", "value": w},
                ]},
            },
            blit("swap", "minecraft:main"),
        ],
    }


def motion(k):
    return {
        "targets": {"swap": {}, "accum": {"persistent": True}},
        "passes": [
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "nocturnum:post/mblur",
                "inputs": [{"sampler_name": "In", "target": "minecraft:main"}, {"sampler_name": "Prev", "target": "accum"}],
                "output": "swap",
                "uniforms": {"BlurConfig": [{"name": "Strength", "type": "float", "value": round(k, 2)}]},
            },
            blit("swap", "accum"),
            blit("swap", "minecraft:main"),
        ],
    }


for i, s in enumerate(SAT):
    for j, c in enumerate(CON):
        for k, w in enumerate(WARM):
            with open(f"{OUT}/grade_{i}_{j}_{k}.json", "w", encoding="utf-8") as f:
                json.dump(grade(s, c, w), f, indent=1)
for i, b in enumerate(BLUR, start=1):
    with open(f"{OUT}/blur_{i}.json", "w", encoding="utf-8") as f:
        json.dump(motion(b), f, indent=1)

GRADE_BODY = """
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform GradeConfig {
    float Saturation;
    float Contrast;
    float Warmth;
};

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, Saturation);
    c = (c - 0.5) * Contrast + 0.5;
    c += vec3(Warmth * 0.04, Warmth * 0.01, -Warmth * 0.04);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""

BLUR_BODY = """
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    float Strength;
};

void main() {
    vec3 cur = texture(InSampler, texCoord).rgb;
    vec3 prev = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(cur, prev, Strength), 1.0);
}
"""


def write_shaders(version_dir, header):
    d = f"{version_dir}/src/main/resources/assets/nocturnum/shaders/post"
    os.makedirs(d, exist_ok=True)
    with open(f"{d}/grade.fsh", "w", encoding="utf-8") as f:
        f.write(header("uniform sampler2D InSampler;") + GRADE_BODY)
    with open(f"{d}/mblur.fsh", "w", encoding="utf-8") as f:
        f.write(header("uniform sampler2D InSampler;\nuniform sampler2D PrevSampler;") + BLUR_BODY)


# 1.21.11 style (GLSL 330, plain in/out)
write_shaders("v1_21_11", lambda samplers: f"#version 330\n\n{samplers}\n\nin vec2 texCoord;\nout vec4 fragColor;\n")
# 26.3 style (separate shader objects, explicit locations)
write_shaders("v26_3", lambda samplers: "#version 330\n#extension GL_ARB_separate_shader_objects : require\n\n"
              f"{samplers}\n\nlayout(location = 0) in vec2 texCoord;\nlayout(location = 0) out vec4 fragColor;\n")
print("post effects generated:", len(SAT) * len(CON) * len(WARM), "grades,", len(BLUR), "blurs")
