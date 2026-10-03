package uk.co.fireburn.lochssh.ssh

object PerformanceCommand {
    private val script = """
        LC_ALL=C
        export LC_ALL
        [ -r /proc/stat ] || exit 65
        printf 'LOCHSSH:UPTIME\n'
        cat /proc/uptime 2>/dev/null
        printf '\nLOCHSSH:CPU\n'
        head -n 1 /proc/stat 2>/dev/null
        printf '\nLOCHSSH:LOAD\n'
        cat /proc/loadavg 2>/dev/null
        printf '\nLOCHSSH:MEMORY\n'
        cat /proc/meminfo 2>/dev/null
        printf '\nLOCHSSH:NETWORK\n'
        cat /proc/net/dev 2>/dev/null
        printf '\nLOCHSSH:DISK\n'
        df -Pk / 2>/dev/null
        printf '\nLOCHSSH:TEMPERATURE\n'
        for loch_temperature_zone in /sys/class/thermal/thermal_zone*; do
            [ -r "${'$'}loch_temperature_zone/temp" ] || continue
            loch_temperature_type=${'$'}(cat "${'$'}loch_temperature_zone/type" 2>/dev/null)
            printf '%s\t' "${'$'}loch_temperature_type"
            cat "${'$'}loch_temperature_zone/temp" 2>/dev/null
        done
        for loch_monitor in /sys/class/hwmon/hwmon*; do
            loch_monitor_name=${'$'}(cat "${'$'}loch_monitor/name" 2>/dev/null)
            for loch_sensor in "${'$'}loch_monitor"/temp*_input; do
                [ -r "${'$'}loch_sensor" ] || continue
                loch_sensor_label=${'$'}(cat "${'$'}{loch_sensor%_input}_label" 2>/dev/null)
                [ -n "${'$'}loch_sensor_label" ] || loch_sensor_label=${'$'}{loch_sensor##*/}
                printf '%s %s\t' "${'$'}loch_monitor_name" "${'$'}loch_sensor_label"
                cat "${'$'}loch_sensor" 2>/dev/null
            done
        done
        exit 0
    """.trimIndent()

    val command = "sh -c '" + script.replace("'", "'\\''") + "'"
}
