#version 430 core

layout(local_size_x_id = 0) in;

// resources
layout(std430, binding = 0) readonly buffer in_rules { uint rules[]; };
layout(std430, binding = 1) buffer in_chunks { double chunks[]; };
layout(std430, binding = 2) writeonly buffer out_res { bool res[]; };

// Settings
const int itpf = 33333334;
const int chunk_size = 2048;
const int chunk_pow = 11;
const float directions_x[4] = float[4](0, 1, 0, -1);
const float directions_y[4] = float[4](-1, 0, 1, 0);

uint state[64];
uint dir;
uint rule_size;

void build_state() {
    uint id = gl_GlobalInvocationID.x;
    uint rule_low = rules[2 * id];
    uint rule_high = rules[(2 * id) + 1];
    for (int i = 0; rule_low != 0; i++) {
        state[i] = ((rule_low & 1) == 1) ? 1 : 3;
        rule_low /= 2;
        rule_size += 1;
    }
    for (int i = 0; rule_high != 0; i++) {
        state[32 + i] = ((rule_high & 1) == 1) ? 1 : 3;
        rule_high /= 2;
        rule_size += 1;
    }
}

void main(void) {
    uint id = gl_GlobalInvocationID.x;
    int x = chunk_size / 2;
    int y = chunk_size / 2;
    build_state();
    for (int i = 0; i < itpf; i++) {
        if (x >= chunk_size || x < 0 || y >= chunk_size || y < 0) {
            res[id] = 1;
            break;
        }
        int index = x | (y << chunk_pow);
        int chunk_id = chunk_size * id + index;
        dir = (dir + state[chunks[chunk_id]) & 3;
        chunks[chunk_id] += 1;
        if (chunks[chunk_id] == rule_size) {
            chunks[chunk_id] = 0;
        }
        x += directions_x[dir];
        y += directions_y[dir];
    }
}