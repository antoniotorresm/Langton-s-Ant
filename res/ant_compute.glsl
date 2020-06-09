#version 430 core

layout(local_size_x = 32) in;

// resources
layout(std430, binding = 0) readonly buffer in_states { int states[]; };
layout(std430, binding = 1) buffer buf_chunks { int chunks[]; };
layout(std430, binding = 2) buffer buf_res { int res[]; };
layout(std430, binding = 3) buffer buf_dir { int dirs[]; };
layout(std430, binding = 4) readonly buffer buf_rule_size { int rule_sizes[]; };
layout(std430, binding = 5) buffer buf_pos { int positions[]; };

// Settings
const int itpf = 1000000;
const int chunk_pow = 11;
const int chunk_size = 1<<chunk_pow;
const int directions[4] = int[4](-chunk_size, 1, chunk_size, -1);
const int directions_x[4] = int[4](0, 1, 0, -1);

void main(void) {
    int id = int(gl_GlobalInvocationID.x);
    if (res[id] == 1) {
        return; // Already finished this ant
    }

    int cindex = positions[id];
    int x = cindex & (chunk_size-1);
    int d = dirs[id];

    for (int i = 0; i < itpf; i++) {
        if (x < 0 || x >= chunk_size || cindex < 0 || cindex >= chunk_size*chunk_size) {
//            positions[2*id] = x;
//            positions[(2 * id) + 1] = y;
//            dirs[id] = d;
            res[id] = 1;
            return;
        }

        int index = (id<<(chunk_pow<<1)) | cindex;
        d = (d + states[(id<<6)|chunks[index]]) & 3;
        chunks[index] += 1;
        if (chunks[index] == rule_sizes[id]) {
            chunks[index] = 0;
        }
        cindex += directions[d];
        x += directions_x[d];
    }
    positions[id] = cindex;
    dirs[id] = d;
    res[id] = 0;
}
