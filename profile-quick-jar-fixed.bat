@echo off
REM Fixed: Correct JMH profiler syntax for JAR mode
REM Usage: profile-quick-jar-fixed.bat

echo ========================================
echo Quick Map Field Profiling (JAR Mode)
echo Fixed Syntax Version
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

echo [1/2] GC Profiling (Memory Allocation)
echo ----------------------------------------
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000 -prof gc

echo.
echo.
echo [2/2] Stack Profiling (CPU Hotspots)
echo ----------------------------------------
REM CORRECT SYNTAX: Use colon (;) as separator, not comma
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000 -prof "stack:lines=10;top=20"

echo.
echo ========================================
echo Profiling Complete!
echo.
echo Look for:
echo   - gc.alloc.rate.norm (bytes/op)
echo   - Top methods in stack trace
echo ========================================
pause
