# Регрессионный тест дюпа (офф-хенд)

Проверяет баг из отзыва на 2.0.5:
> «если взять шалкер в левую руку, то можно постоянно ставить один и тот же шалкер»

Причина: старый код при Shift+ПКМ всегда списывал предмет из **главной** руки
(`setItem(getHeldItemSlot(), null)`), а офф-хенд оставался нетронутым.

## Запуск

```powershell
# 1. собрать и установить плагин в локальный maven-репозиторий
cd D:\MineCraft\plugins\plugins\ShulkerKing
mvn -DskipTests clean package
mvn install:install-file -Dfile=target\ShulkerKing-2.0.6.jar `
    -DgroupId=com.shulkerking -DartifactId=ShulkerKing -Dversion=2.0.6 -Dpackaging=jar

# 2. прогнать тесты
cd tests\dup-regression
mvn test
```

Ожидаемо: `Tests run: 4, Failures: 0, Errors: 0`.

## Что покрыто

| Тест | Проверка |
|---|---|
| `offhandConsumptionDoesNotTouchMainHand` | офф-хенд списывается, главная рука цела |
| `offhandStackDecrementsByOne` | из стака уходит ровно один предмет |
| `repeatedOffhandPlacementStopsWhenEmpty` | второй раз ставить нечего, главная рука не страдает |
| `mainHandConsumptionStillWorks` | обычный путь из главной руки не сломан |

Тесты работают на MockBukkit (Paper API 1.21), поэтому клиент и живой сервер не нужны.
