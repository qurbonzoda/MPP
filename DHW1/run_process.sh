#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

javac src/ru/ifmo/Main.java
java -classpath ./src ru.ifmo.Main $1 $DIR/process.cfg
