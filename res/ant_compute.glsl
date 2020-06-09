#version 430 core

layout(local_size_x = 32) in;

// resources
layout(std430, binding = 0) readonly buffer in_states { int states[]; };
layout(std430, binding = 1) buffer buf_chunks { int chunks[]; };
layout(std430, binding = 2) buffer buf_res { int res[]; };
layout(std430, binding = 3) buffer buf_dir { int dirs[]; };
layout(std430, binding = 4) readonly buffer buf_rule_size { int rule_sizes[]; };
layout(std430, binding = 5) buffer buf_pos { uint positions[]; };

// Settings
const int itpf = 5000000;//33333334;
const int chunk_pow = 11;
const int chunk_size = 1<<chunk_pow;
const int directions_x[4] = int[4](0, 1, 0, -1);
const int directions_y[4] = int[4](-1, 0, 1, 0);

//void build_state() {
//    uint id = gl_GlobalInvocationID.x;
//    uint rule_low = rules[2 * id];
//    uint rule_high = rules[(2 * id) + 1];
//    rule_size = 0U;
//    for (int i = 0; rule_low != 0; i++) {
//        state[i] = ((rule_low & 1U) == 1) ? 1 : 3;
//        rule_low /= 2;
//        rule_size += 1;
//    }
//    if (rule_high != 0) {
//        rule_size = 32;
//    }
//    for (uint i = rule_size; rule_high != 0; i++) {
//        state[i] = ((rule_high & 1U) == 1) ? 1 : 3;
//        rule_high /= 2;
//        rule_size += 1;
//    }
//}

void main(void) {
    uint id = gl_GlobalInvocationID.x;
    if (res[id] == 1) {
        return; // Already finished this ant
    }
    uint x = positions[2 * id];
    uint y = positions[(2 * id) + 1];
    //build_state();
    //for(uint i = 0; i < chunk_size*chunk_size; i++) {
    //	chunk[i | (id<<(chunk_pow<<1))] = 0U;
    //}
    for (int i = 0; i < itpf; i++) {
        if (x >= chunk_size || x < 0 || y >= chunk_size || y < 0) {
            res[id] = 1;
            return;
        }
        uint index = (id<<(chunk_pow<<1)) | x | (y << chunk_pow);
        dirs[id] = (dirs[id] + states[chunks[index]]) & 3U;
        chunks[index] += 1;
        if (chunks[index] == rule_sizes[id]) {
            chunks[index] = 0U;
        }
        positions[2 * id] += directions_x[dirs[id]];
        positions[(2 * id) + 1] += directions_y[dirs[id]]; // 11, 35, 47
    }
    res[id] = 0;
}
