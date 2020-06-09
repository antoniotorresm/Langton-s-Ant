#version 430 core

layout(local_size_x = 16) in;

// resources
layout(std430, binding = 0) readonly buffer in_states { int states[]; };
layout(std430, binding = 1) buffer buf_chunks { int chunks[]; };
layout(std430, binding = 2) buffer buf_res { int res[]; };
layout(std430, binding = 3) buffer buf_dir { int dirs[]; };
layout(std430, binding = 4) readonly buffer buf_rule_size { int rule_sizes[]; };
layout(std430, binding = 5) buffer buf_pos { int positions[]; };

// Settings
const int itpf = 1000;//33333334;
const int chunk_pow = 11;
const int chunk_size = 1<<chunk_pow;
const int directions_x[4] = int[4](0, 1, 0, -1);
const int directions_y[4] = int[4](-1, 0, 1, 0);

void main(void) {
    int id = int(gl_GlobalInvocationID.x);
    if (res[id] == 1) {
        return; // Already finished this ant
    }

    int x = positions[2 * id];
    int y = positions[(2 * id) + 1];
    int d = dirs[id];

    for (int i = 0; i < itpf; i++) {
        if (x >= chunk_size || x < 0 || y >= chunk_size || y < 0) {
            positions[2*id] = x;
            positions[(2 * id) + 1] = y;
            dirs[id] = d;
            res[id] = 1;
            return;
        }
        int index = (id<<(chunk_pow<<1)) | x | (y << chunk_pow);
        d = (d + states[chunks[index]]) & 3;
        chunks[index] += 1;
        if (chunks[index] == rule_sizes[id]) {
            chunks[index] = 0;
        }
        x += directions_x[d];
        y += directions_y[d]; // 11, 35, 47
    }
    positions[2*id] = x;
    positions[(2 * id) + 1] = y;
    dirs[id] = d;
    res[id] = 0;
}
