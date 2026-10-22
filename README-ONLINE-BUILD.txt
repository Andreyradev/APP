Zebra GX430t Controller — онлайн компилиране без Android Studio

1. Влез в https://github.com/ и създай безплатен профил, ако нямаш.
2. Натисни + (горе вдясно) > New repository.
3. Дай име, например ZebraGX430tController, избери Private или Public и натисни Create repository.
4. В новото хранилище избери Add file > Upload files.
5. Разархивирай ZIP проекта на компютъра и качи СЪДЪРЖАНИЕТО на папката ZebraGX430tAndroid (не самия ZIP). Важно: включи и скритата папка .github, съдържаща workflows/build-apk.yml. Ако качването през браузъра не позволява папки/скрити файлове, използвай GitHub web editor или GitHub Desktop; не е нужно Android Studio.
6. Натисни Commit changes.
7. Отвори раздел Actions в хранилището. Избери “Build Zebra GX430t Controller APK”. Натисни Run workflow > Run workflow.
8. Изчакай задачата да стане зелена (успешна), отвори изпълнението и в секцията Artifacts изтегли ZebraGX430tController-debug-apk.
9. Разархивирай изтегления artifact ZIP. Вътре трябва да има app-debug.apk.
10. Прехвърли APK на Android телефона и го отвори за инсталиране. Android може да поиска разрешение за инсталиране от този източник.

Бележки:
- APK-то е debug build за тестване, не публикувана версия в Google Play.
- Проектът трябва да премине успешно компилацията; ако Actions покаже грешка, отвори лога и използвай съобщението за отстраняване.
- Телефонът и Zebra принтерът трябва да са в една и съща локална мрежа; принтерът трябва да е достъпен на TCP порт 9100.
