import sys
import asyncio
import logging

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)

def main():
    from user_recognition_agent.server import main as recognition_main
    asyncio.run(recognition_main())

if __name__ == "__main__":
    main()
