@echo off
REM Quick profiling script for Map Field Benchmark
REM Usage: profile-quick.bat

echo ========================================
echo Quick Map Field Profiling
echo ========================================
echo.

echo [1/2] GC Profiling (Memory Allocation)
echo ----------------------------------------
call mvn --% exec:java -Dexec.mainClass=org.openjdk.jmh.Main -Dexec.args="-prof gc MapFieldBenchmark.parseMapFields -p mapSize=1000"

echo.
echo.
echo [2/2] Stack Profiling (CPU Hotspots)
echo ----------------------------------------
call mvn --% exec:java -Dexec.mainClass=org.openjdk.jmh.Main  -Dexec.args="-prof stack:lines=10;top=20 MapFieldBenchmark.parseMapFields -p mapSize=1000"

echo.
echo ========================================
echo Profiling Complete!
echo.
echo Look for hottest methods in output above
echo ========================================
pause
