#!/bin/sh
case "`uname`" in
    CYGWIN*)
        CFILE = `cygpath "$0"`
        RESOLVED_NAME=`readlink -f "$CFILE"`
        ;;
    Darwin*)
        RESOLVED_NAME=`readlink "$0"`
        ;;
    FreeBSD)
        RESOLVED_NAME=`readlink -f "$0"`
        ;;
    OpenBSD)
        RESOLVED_NAME=`readlink -f "$0"`
        JAVA_HOME=`/usr/local/bin/javaPathHelper -h keycloak`
        ;;
    Linux)
        RESOLVED_NAME=`readlink -f "$0"`
        ;;
esac

RESOLVED_NAME="${RESOLVED_NAME:-"$0"}"

DIRNAME=`dirname "$RESOLVED_NAME"`

if [ -z "$JAVA" ]; then
    if [ -n "$JAVA_HOME" ]; then
        JAVA="$JAVA_HOME/bin/java"
    else
        JAVA="java"
    fi
fi

"$JAVA" $KC_OPTS -cp $DIRNAME/client/keycloak-admin-cli-${project.version}.jar --add-opens=java.base/java.security=ALL-UNNAMED -Dkc.lib.dir=$DIRNAME/client/lib org.keycloak.client.admin.cli.KcAdmMain "$@"
