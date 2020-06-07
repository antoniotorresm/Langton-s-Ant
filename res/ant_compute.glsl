#version 430 core

layout(local_size_x_id = 0) in;

// resources
layout(std430, binding = 0) readonly buffer in_rules { uint rules[]; };
layout(std430, binding = 1) writeonly buffer out_res { uint res[]; };

// Settings
const int itpf = 33333334;
const int chunk_size = 2048;
const int chunk_pow = 11;
const float directions_x[4] = float[4](0, 1, 0, -1);
const float directions_y[4] = float[4](-1, 0, 1, 0);

uint state[64];
uint chunk[chunk_size * chunk_size];
uint dir;
uint rule_size;

void build_state() {
    uint id = gl_GlobalInvocationID.x;
    uint rule_low = rules[2 * id];
    uint rule_high = rules[(2 * id) + 1];
    rule_size = 0;
    for (int i = 0; rule_low != 0; i++) {
        state[i] = ((rule_low & 1) == 1) ? 1 : 3;
        rule_low /= 2;
        rule_size += 1;
    }
    if (rule_high != 0) {
        rule_size = 32;
    }
    for (int i = rule_size; rule_high != 0; i++) {
        state[i] = ((rule_high & 1) == 1) ? 1 : 3;
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
        dir = (dir + state[chunks[index]) & 3;
        chunks[index] += 1;
        if (chunks[index] == rule_size) {
            chunks[index] = 0;
        }
        x += directions_x[dir];
        y += directions_y[dir];
    }
    res[id] = 0;
}