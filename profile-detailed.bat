@echo off
REM Detailed profiling suite with all analyzers
REM Usage: profile-detailed.bat

echo ========================================
echo Detailed Map Field Profiling Suite
echo ========================================
echo.
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
cd profiling-results

echo.
echo ========================================
echo [1/4] GC Profiling - Memory Allocation
echo ========================================
call mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main -Dexec.classpathScope=test -Dexec.args="-prof gc -rf csv -rff gc-profile.csv MapFieldBenchmark.parseMapFields"

echo.
echo ========================================
echo [2/4] Stack Profiling - CPU Hotspots
echo ========================================
call mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main -Dexec.classpathScope=test -Dexec.args="-prof stack:lines=10;top=20 -rf csv -rff stack-profile.csv MapFieldBenchmark.parseMapFields"

echo.
echo ========================================
echo [3/4] Performance Counters
echo ========================================
call mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main -Dexec.classpathScope=test -Dexec.args="-prof perfnorm -rf csv -rff perf-profile.csv MapFieldBenchmark.parseMapFields"

echo.
echo ========================================
echo [4/4] JFR Recording - Detailed Analysis
echo ========================================
call mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main -Dexec.classpathScope=test -Dexec.args="-prof jfr -rf json -rff jfr-results.json MapFieldBenchmark.parseMapFields"

cd ..

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
