@echo off
REM Detailed profiling suite for JMH JAR
REM Usage: profile-detailed-jar.bat

echo ========================================
echo Detailed Map Field Profiling (JAR Mode)
echo ========================================
echo.

REM Check if JAR exists
if not exist target\jmh-benchmarks.jar (
    echo ERROR: target\jmh-benchmarks.jar not found!
    echo.
    echo Please build the benchmark JAR first:
    echo   mvn clean package -DskipTests
    echo.
    pause
    exit /b 1
)

echo This will run 4 profiling passes:
echo   1. GC Profiler (memory allocation)
echo   2. Stack Profiler (CPU hotspots)
echo   3. Performance Counters (CPU cache, branches)
echo   4. JFR Recording (detailed analysis)
echo.
echo Estimated time: 10-15 minutes
echo.
pause

REM Create results directory
if not exist profiling-results mkdir profiling-results

echo.
echo ========================================
echo [1/4] GC Profiling - Memory Allocation
echo ========================================
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000 -prof gc -rf csv -rff profiling-results\gc-profile.csv

echo.
echo ========================================
echo [2/4] Stack Profiling - CPU Hotspots
echo ========================================
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000 -prof stack:lines=10,top=20 -rf csv -rff profiling-results\stack-profile.csv

echo.
echo ========================================
echo [3/4] Performance Counters
echo ========================================
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000 -prof perfnorm -rf csv -rff profiling-results\perf-profile.csv

echo.
echo ========================================
echo [4/4] JFR Recording - Detailed Analysis
echo ========================================
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000 -prof jfr -rf json -rff profiling-results\jfr-results.json

echo.
echo ========================================
echo Profiling Complete!
echo ========================================
echo.
echo Results saved in: profiling-results\
echo.
echo Files created:
dir /b profiling-results
echo.
echo Next steps:
echo   1. Check gc-profile.csv for allocation rates
echo   2. Check stack-profile.csv for hottest methods
echo   3. Open *.jfr files with JDK Mission Control (jmc.exe)
echo   4. Upload jfr-results.json to https://jmh.morethan.io/
echo.
echo ========================================
pause
