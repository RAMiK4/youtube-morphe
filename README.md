# YouTube Morphe

Сборки YouTube с официальными патчами [Morphe](https://github.com/MorpheApp/morphe-patches).

**Скачать:** [последний релиз](https://github.com/RAMiK4/youtube-morphe/releases/latest)

| Файл | Пакет |
|---|---|
| `YouTube_Morphe-v<версия>-p<патчи>_[Original+]_Mod_(BBK Clone).apk` | `com.hiby.music` |
| `YouTube_Morphe-v<версия>-p<патчи>_[Amoled+]_Mod.apk` | `app.morphe.android.youtube` |

Сборки подписаны тем же ключом, что и версии с 4PDA, поэтому обновление устанавливается поверх без удаления.

---

## Сборка (для автора)

1. **Releases → Draft a new release**, тег `source`, прикрепить оригинальный APK YouTube и сохранить как черновик. При смене версии YouTube заменить APK в этом черновике.
2. Обновить `changelog/ru.md` и `changelog/en.md` (каждая строка с ✓; другие языки — `changelog/<язык>.md`), затем **Actions → Release YouTube Morphe → Run workflow** (патчи: `latest`, `dev` или тег, напр. `v1.48.0`).

Секреты (Settings → Secrets and variables → Actions):

| Имя | Тип | Значение |
|---|---|---|
| `KEYSTORE_B64` | secret | keystore в base64 |
| `KEYSTORE_PASSWORD` | secret | пароль хранилища (пусто — по умолчанию) |
| `KEY_ALIAS` | secret | alias ключа (пусто — `Morphe`) |
| `KEY_PASSWORD` | secret | пароль ключа (пусто — `Morphe`) |
| `CERT_SHA256` | variable | отпечаток SHA-256 сертификата для проверки подписи |

Собираются два варианта, в обоих цвета темы меняются в настройках приложения:

- `[Original+]` (`com.hiby.music`) — по умолчанию тёмная #0F0F0F, светлая #F0F0F0, текст цветом другой темы;
- `[Amoled+]` (`app.morphe.android.youtube`) — по умолчанию тёмная #000000, светлая #FFFFFF.

Включены все патчи YouTube + «Change installer source» и дополнение [YaVoT](https://github.com/sashade8-ship-it/morphe-patches-yavot) (Yandex voice-over translation). Патчи собираются из официальной версии Morphe с дополнениями из `extras/`.

Рекомендуемые настройки — `settings/defaults.json` (формат экспорта настроек Morphe). Каждая настройка применяется один раз: при первой установке или в обновлении, где она добавлена. Изменения пользователя сохраняются.

Проверка обновлений внутри приложения (Настройки Morphe → Обновления) читает `updates/hiby.json` и `updates/morphe.json`.

`branding/` — своя иконка (`mipmap-*`), логотип в шапке и значок уведомлений (`drawable-*`).
