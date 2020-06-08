#version 430 core

layout(local_size_x = 32) in;

// resources
layout(std430, binding = 0) readonly buffer in_rules { uint rules[];};
layout(std430, binding = 1) buffer in_chunk { uint chunk[]; };
layout(std430, binding = 2) writeonly buffer out_res { uint res[]; };

// Settings
const int itpf = 10000000;//33333334;
const int chunk_pow = 11;
const int chunk_size = 1<<chunk_pow;
const int directions_x[4] = int[4](0, 1, 0, -1);
const int directions_y[4] = int[4](-1, 0, 1, 0);

uint state[64];
uint dir = 0;
uint rule_size;

void build_state() {
    uint id = gl_GlobalInvocationID.x;
    uint rule_low = rules[2 * id];
    uint rule_high = rules[(2 * id) + 1];
    rule_size = 0U;
    for (int i = 0; rule_low != 0; i++) {
        state[i] = ((rule_low & 1U) == 1) ? 1 : 3;
        rule_low /= 2;
        rule_size += 1;
    }
    if (rule_high != 0) {
        rule_size = 32;
    }
    for (uint i = rule_size; rule_high != 0; i++) {
        state[i] = ((rule_high & 1U) == 1) ? 1 : 3;
        rule_high /= 2;
        rule_size += 1;
    }
}

void main(void) {
    uint id = gl_GlobalInvocationID.x;
    uint x = chunk_size / 2;
    uint y = chunk_size / 2;
    build_state();
    for(int i = 0; i < chunk_size*chunk_size; i++) {
    	chunk[i] = 0U;
    }

    for (int i = 0; i < 100000; i++) {
        if (x >= chunk_size || x < 0 || y >= chunk_size || y < 0) {
            res[id] = 1;
            return;
        }

        uint index = (id<<(chunk_pow<<1)) | x | (y << chunk_pow);
        dir = (dir + state[chunk[index]]) & 3U;
        chunk[index] += 1;
        if (chunk[index] == rule_size) {
            chunk[index] = 0U;
        }
        x += directions_x[dir];
        y += directions_y[dir]; // 11, 35, 47
    }
    res[id] = 0;
}
