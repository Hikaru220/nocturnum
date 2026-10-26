#version 330

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

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
