@echo off
REM Setup script for Trello MCP Server (Windows)

echo ===================================
echo Trello MCP Server Setup
echo ===================================
echo.

REM Check Python version
echo Checking Python version...
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python 3 is not installed
    echo Please install Python 3.10 or higher
    exit /b 1
)

for /f "tokens=2" %%i in ('python --version 2^>^&1') do set PYTHON_VERSION=%%i
echo Python %PYTHON_VERSION% found
echo.

REM Create virtual environment
echo Creating virtual environment...
if exist venv (
    echo Virtual environment already exists. Removing...
    rmdir /s /q venv
)

python -m venv venv
if errorlevel 1 (
    echo ERROR: Failed to create virtual environment
    exit /b 1
)
echo Virtual environment created
echo.

REM Activate virtual environment
echo Activating virtual environment...
call venv\Scripts\activate.bat
echo Virtual environment activated
echo.

REM Upgrade pip
echo Upgrading pip...
python -m pip install --upgrade pip >nul 2>&1
echo pip upgraded
echo.

REM Install dependencies
echo Installing dependencies...
pip install -r requirements.txt
if errorlevel 1 (
    echo ERROR: Failed to install dependencies
    exit /b 1
)
echo Dependencies installed
echo.

REM Check for .env file
echo Checking configuration...
if not exist .env (
    echo .env file not found. Creating from template...
    copy .env.example .env >nul
    echo .env file created
    echo.
    echo =========================================
    echo IMPORTANT: Configure your credentials
    echo =========================================
    echo.
    echo Please edit .env file and set:
    echo   - TRELLO_API_KEY
    echo   - TRELLO_TOKEN
    echo.
    echo To get your credentials:
    echo   1. Visit: https://trello.com/power-ups/admin
    echo   2. Generate your API Key
    echo   3. Generate a Token
    echo.
    echo See doc/trello-setup-guide.md for detailed instructions
    echo.
    exit /b 0
) else (
    echo .env file found
)

REM Load .env variables (basic parsing)
for /f "usebackq tokens=1,2 delims==" %%a in (".env") do (
    set %%a=%%b
)

REM Validate credentials
echo Validating Trello credentials...
if "%TRELLO_API_KEY%"=="" (
    echo ERROR: TRELLO_API_KEY not configured in .env
    echo Please edit .env and set your Trello API key
    exit /b 1
)

if "%TRELLO_API_KEY%"=="your_api_key_here" (
    echo ERROR: TRELLO_API_KEY not configured in .env
    echo Please edit .env and set your Trello API key
    exit /b 1
)

if "%TRELLO_TOKEN%"=="" (
    echo ERROR: TRELLO_TOKEN not configured in .env
    echo Please edit .env and set your Trello token
    exit /b 1
)

if "%TRELLO_TOKEN%"=="your_token_here" (
    echo ERROR: TRELLO_TOKEN not configured in .env
    echo Please edit .env and set your Trello token
    exit /b 1
)

echo Trello credentials configured
echo.

echo ===================================
echo Setup completed successfully!
echo ===================================
echo.
echo To start the server:
echo   1. Activate virtual environment: venv\Scripts\activate.bat
echo   2. Run server: python -m src.main
echo.
echo To integrate with Claude Code:
echo   - Add server configuration to .mcp.json
echo   - See README.md for details
echo.
