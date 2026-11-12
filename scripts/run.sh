#!/usr/bin/env sh
# Run the app without Docker. Needs JDK 21 and nothing else.
#
#   scripts/run.sh            restart in the background (default)
#   scripts/run.sh start
#   scripts/run.sh stop
#   scripts/run.sh build      just build the jar
set -eu
cd "$(dirname "$0")/.."

PORT="${PORT:-3000}"
JAR="app/build/libs/cyanotypes.jar"
PIDFILE="app.pid"
LOGFILE="app.log"

start() {
    [ -f "$JAR" ] || ./gradlew :app:bootJar
    if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
        echo "already running as pid $(cat "$PIDFILE")"
        return 0
    fi
    # setsid detaches the JVM from this shell's session so it survives the terminal.
    PORT="$PORT" nohup setsid java -jar "$JAR" >>"$LOGFILE" 2>&1 </dev/null &
    echo $! >"$PIDFILE"
    echo "started pid $(cat "$PIDFILE") on port $PORT; log: $LOGFILE"
}

stop() {
    if [ -f "$PIDFILE" ]; then
        pid=$(cat "$PIDFILE")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            echo "stopped pid $pid"
        else
            echo "no process for pid $pid (stale pid file)"
        fi
        rm -f "$PIDFILE"
    else
        echo "not running (no pid file)"
    fi
}

case "${1:-restart}" in
    build)   ./gradlew :app:bootJar ;;
    start)   start ;;
    stop)    stop ;;
    restart) stop; sleep 1; start ;;
    *)       echo "usage: $0 [start|stop|restart|build]" >&2; exit 2 ;;
esac
