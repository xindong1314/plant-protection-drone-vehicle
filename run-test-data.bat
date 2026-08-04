@echo off
docker exec -i ppdv-mysql mysql -u ppdv -pppdv123 --default-character-set=utf8mb4 ppdv < test-data.sql
echo Done.
pause
