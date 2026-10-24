# YouTube Morphe

Сборки YouTube с патчами [Morphe](https://github.com/MorpheApp/morphe-patches) и дополнениями из [RAMiK4/morphe-patches](https://github.com/RAMiK4/morphe-patches).

**Скачать:** [последний релиз](https://github.com/RAMiK4/youtube-morphe/releases/latest)

| Файл | Пакет |
|---|---|
| `YouTube-Morphe-<версия>-HiBy.apk` | `com.hiby.music` |
| `YouTube-Morphe-<версия>.apk` | `app.morphe.android.youtube` |

Сборки подписаны тем же ключом, что и версии с 4PDA, поэтому обновление устанавливается поверх без удаления.

---

## Сборка (для автора)

1. **Releases → Draft a new release**, тег `source`, прикрепить оригинальный APK YouTube и сохранить как черновик. При смене версии YouTube заменить APK в этом черновике.
2. Обновить `CHANGELOG.md` (каждая строка с ✓), затем **Actions → Release YouTube Morphe → Run workflow** (патчи: `latest`, `dev` или тег, напр. `v1.48.0`).

Секреты (Settings → Secrets and variables → Actions):

| Имя | Тип | Значение |
|---|---|---|
| `KEYSTORE_B64` | secret | keystore в base64 |
| `KEYSTORE_PASSWORD` | secret | пароль хранилища (пусто — по умолчанию) |
| `KEY_ALIAS` | secret | alias ключа (пусто — `Morphe`) |
| `KEY_PASSWORD` | secret | пароль ключа (пусто — `Morphe`) |
| `CERT_SHA256` | variable | отпечаток SHA-256 сертификата для проверки подписи |

Собираются два варианта: `-HiBy` (пакет `com.hiby.music`, тема #0F0F0F / #F0F0F0) и стандартный (`app.morphe.android.youtube`, тема меняется в приложении). Включены все патчи YouTube + «Change installer source».

`branding/icon/` — своя иконка (`mipmap-*`), `branding/header/` — свой логотип в шапке (`drawable-*`).
