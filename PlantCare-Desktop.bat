@echo off
:: PlantCare Desktop Launcher
:: Запускает desktop версию PlantCare

echo Starting PlantCare Desktop...
echo.

:: Проверяем наличие EXE файла
if exist "desktop\build\compose\binaries\main-release\exe\PlantCare-0.1.0.exe" (
    echo Found PlantCare executable!
    echo Launching...
    echo.
    start "" "desktop\build\compose\binaries\main-release\exe\PlantCare-0.1.0.exe"
    exit
) else (
    echo PlantCare executable not found!
    echo Building desktop application...
    echo.
    
    :: Собираем desktop приложение
    call gradlew.bat :desktop:packageReleaseExe
    
    if exist "desktop\build\compose\binaries\main-release\exe\PlantCare-0.1.0.exe" (
        echo.
        echo Build successful! Launching PlantCare...
        echo.
        start "" "desktop\build\compose\binaries\main-release\exe\PlantCare-0.1.0.exe"
    ) else (
        echo.
        echo ERROR: Failed to build PlantCare!
        echo Please check the build output above.
        pause
    )
)
