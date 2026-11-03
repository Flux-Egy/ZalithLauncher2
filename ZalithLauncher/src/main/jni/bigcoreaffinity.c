//
// Created by maks on 19.06.2023.
//

#define _GNU_SOURCE // we are GNU GPLv3

#include <linux/limits.h>
#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sched.h>
#include <string.h>

#include <errno.h>

#define FREQ_MAX 256
void bigcore_format_cpu_path(char* buffer, unsigned int cpu_core) {
    snprintf(buffer, PATH_MAX, "/sys/devices/system/cpu/cpu%u/cpufreq/cpuinfo_max_freq", cpu_core);
}

/* Highest cpuinfo_max_freq among all configured CPUs (offline/missing ones are skipped) */
static unsigned int bigcore_find(unsigned long* out_freq) {
    char path_buffer[PATH_MAX];
    char freq_buffer[FREQ_MAX];
    char* discard;
    unsigned long max_freq = 0;
    unsigned int big_core_id = 0;
    long configured = sysconf(_SC_NPROCESSORS_CONF);
    if (configured <= 0 || configured > CPU_SETSIZE) configured = CPU_SETSIZE;
    for (unsigned int core = 0; core < (unsigned int) configured; core++) {
        bigcore_format_cpu_path(path_buffer, core);
        int corefreqfd = open(path_buffer, O_RDONLY);
        // a missing file means the core is offline or has no cpufreq; keep scanning
        if (corefreqfd == -1) continue;
        // Leave room for the terminator: reading FREQ_MAX bytes would make
        // freq_buffer[read_count] write one byte past the end of the buffer.
        ssize_t read_count = read(corefreqfd, freq_buffer, FREQ_MAX - 1);
        close(corefreqfd);
        // read() returns -1 on error; never index freq_buffer[-1]
        if (read_count < 0) read_count = 0;
        freq_buffer[read_count] = 0;
        unsigned long core_freq = strtoul(freq_buffer, &discard, 10);
        // ">=": with equal frequencies the last (usually prime) core wins
        if (core_freq >= max_freq && core_freq > 0) {
            max_freq = core_freq;
            big_core_id = core;
        }
    }
    if (out_freq) *out_freq = max_freq;
    return big_core_id;
}

void bigcore_set_affinity() {
    unsigned long max_freq = 0;
    unsigned int big_core_id = bigcore_find(&max_freq);
    printf("bigcore: big CPU number is %u, frequency %lu Hz\n", big_core_id, max_freq);
    cpu_set_t bigcore_affinity_set;
    CPU_ZERO(&bigcore_affinity_set);
    CPU_SET(big_core_id, &bigcore_affinity_set);
    // the size argument is in bytes (sizeof the set), not CPU_SETSIZE (a bit count)
    if (sched_setaffinity(0, sizeof(bigcore_affinity_set), &bigcore_affinity_set) != 0) {
        printf("bigcore: setting affinity failed: %s\n", strerror(errno));
    } else {
        printf("bigcore: forced current thread onto big core\n");
    }
}
