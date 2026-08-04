# Folia support & vanilla-accurate physics

*[English](#english) · [Русский](#русский)*

---

## English

This branch makes Intave run on **Folia-based servers** (region threading) and, along the way,
fixes a number of places where Intave's movement prediction disagreed with what a **vanilla
client actually does** on 1.21.2+. Most of those physics fixes are not Folia-specific — they
apply to Paper just as well.

The short version: this build tries to model vanilla more closely, so ordinary play — crawling
under a trapdoor, swimming, being shoved in a crowd, riding a piston, standing on a boat or a
happy ghast, sleeping, climbing a ladder, stepping onto a slab — is recognised as ordinary play
rather than flagged.

### What changed, briefly

**Folia / region threading.** Scheduling now routes work to the thread that owns the player's
region: teleports, setbacks, the desync watchdog and block shape resolution no longer touch
chunks or entities from the wrong thread.

**Vanilla accuracy on 1.21.2+.** The client's movement-input packet is used for the movement
keys, jumps are derived from the received motion rather than the held jump bit, and the
crawling/prone speed factor, liquid jumping, auto-step, ladders, bubble columns, pistons,
shulker boxes and beds are modelled the way the client models them.

**Entities.** Entity types, hitbox sizes and slime scaling are resolved without touching live
entity handles, so mobs are hittable and the entity push that vanilla applies to players in a
crowd is part of the prediction.

### Options

Both live in `plugins/Intave/advanced.yml`. If your server runs with `config: THIS` or
`SIMPLE`, that file is not the effective configuration — these two options are looked up in
the effective settings first and then in `advanced.yml` and `config.yml` on disk, so they work
wherever you write them. `/iac diagnostics environment` shows the resolved state.

#### `check.placementanalysis.printer-mode`

```yaml
check:
  placementanalysis:
    printer-mode: false
```

Off by default. When on, block **placement** is reduced to what a vanilla server itself
enforces, so schematic helpers (Litematica's printer and easy-place, build clients) can place
the way they do: placements against air or against a block the player is not looking at, an
unusual block face or hit vector, and long chains of blocks placed against still-unconfirmed
blocks are forwarded to the server instead of being dropped. The aim/timing analysis of
placements and the inventory-click timing checks are not run, since a printer does not aim and
takes its blocks at machine speed.

**Trade-off:** scaffold detection is off while this is on. Breaking, interacting, reach,
movement and combat are untouched. Turn it on only if your server actually wants build clients.

#### `trace`

```yaml
trace: false
```

Off by default, read once at startup. When on, Intave writes detailed diagnostics of its
movement prediction to its log — which branch the simulation took, friction and client input
per tick, and a full dump of every input to a failed prediction.

This is a **false-positive hunting tool**, not something to leave on: it is very noisy, one
line per failing tick per player. Enable it, reproduce the problem, disable it again, and read
the `[FAIL-DEBUG]` lines — they name the state that made the prediction wrong.

### Status

This is a working branch. The Folia work and the physics fixes are in use on a live server, but
some of the more recent fixes are still awaiting confirmation there. Please report false
positives with a `trace: true` log — it is far more useful than the detection message alone.

---

## Русский

Эта ветка позволяет Intave работать на серверах **на базе Folia** (регионная многопоточность)
и попутно исправляет места, где предсказание движения расходилось с тем, что **на самом деле
делает ванильный клиент** на 1.21.2+. Большая часть этих исправлений к Folia отношения не
имеет — на Paper они работают точно так же.

Если коротко: сборка старается точнее повторять ваниль, чтобы обычная игра — ползком под люком,
плавание, толкотня в толпе, поездка на поршне, стойка на лодке или счастливом гасте, сон,
подъём по лестнице, шаг на плиту — распознавалась как обычная игра, а не как нарушение.

### Что изменилось, вкратце

**Folia и регионные потоки.** Планировщик отправляет работу в поток, которому принадлежит
регион игрока: телепорты, откаты, сторож рассинхронизации и разбор форм блоков больше не
трогают чанки и сущности из чужого потока.

**Соответствие ванили на 1.21.2+.** Клавиши движения берутся из пакета ввода клиента, прыжок
определяется по полученному движению, а не по зажатой клавише, и по-клиентски смоделированы
замедление ползком, прыжок в жидкости, авто-шаг, лестницы, пузырьковые колонны, поршни,
шалкеры и кровати.

**Сущности.** Типы, размеры хитбоксов и масштаб слаймов определяются без обращения к живым
объектам сущностей, поэтому мобы бьются, а толчок между сущностями, который ваниль применяет
к игрокам в толпе, учитывается в предсказании.

### Настройки

Обе живут в `plugins/Intave/advanced.yml`. Если сервер запущен с `config: THIS` или `SIMPLE`,
этот файл не является действующей конфигурацией — обе настройки сначала ищутся в действующих
настройках, а затем в `advanced.yml` и `config.yml` на диске, поэтому работают там, где вы их
напишете. Текущее состояние показывает `/iac diagnostics environment`.

#### `check.placementanalysis.printer-mode`

```yaml
check:
  placementanalysis:
    printer-mode: false
```

По умолчанию выключено. Когда включено, проверка **постановки** блоков сводится к тому, что и
так требует ванильный сервер, чтобы помощники по схемам (принтер и easy-place из Litematica,
строительные клиенты) могли ставить блоки так, как они это делают: постановка в воздух или по
блоку, на который игрок не смотрит, необычная грань или точка попадания, длинные цепочки
блоков по ещё неподтверждённым блокам — всё это передаётся серверу, а не отбрасывается. Анализ
прицеливания и таймингов постановки, а также проверки скорости кликов по инвентарю не
запускаются: принтер не целится и берёт блоки с машинной скоростью.

**Цена:** пока это включено, детект скаффолда не работает. Ломание блоков, взаимодействие,
дистанция, движение и бой не затронуты. Включайте, только если серверу действительно нужны
строительные клиенты.

#### `trace`

```yaml
trace: false
```

По умолчанию выключено, читается один раз при запуске. Когда включено, Intave пишет в свой лог
подробную диагностику предсказания движения — какую ветку выбрала симуляция, трение и ввод
клиента по тикам и полный список входных данных для каждого неудачного предсказания.

Это **инструмент для поиска ложных срабатываний**, а не то, что стоит держать включённым:
он очень шумный, по строке на каждый неудачный тик каждого игрока. Включите, воспроизведите
проблему, выключите обратно и читайте строки `[FAIL-DEBUG]` — в них видно, какое состояние
сделало предсказание неверным.

### Состояние

Это рабочая ветка. Работа по Folia и исправления физики используются на живом сервере, но часть
свежих исправлений там ещё не подтверждена. Ложные срабатывания присылайте вместе с логом при
`trace: true` — это гораздо полезнее, чем одно только сообщение о детекте.
