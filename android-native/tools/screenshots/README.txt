Captures TV françaises (docs/screenshots/fr : home, live, guide, detail, series, search, kairo) : depuis 1.2.71, prises sur une
vraie box Android TV avec une vraie source (adb exec-out screencap -p). Jamais d'écran qui montre un identifiant : pas de
Réglages › Sources, pas d'Abonnement, pas d'URL de serveur. La vidéo n'apparaît pas sur une capture (couche matérielle) :
le lecteur reste capturé avec la source fictive ci-dessous.

Autres captures publiques (en, ar, lecteur, profils, langues) : méthode reproductible, SOURCE FICTIVE.

1. Clip de démonstration : ffmpeg -f lavfi -i "gradients=s=1280x720:d=600:speed=0.02" -f lavfi -i sine=f=220:d=600 -c:v libx264 -preset ultrafast -g 50 -pix_fmt yuv420p -c:a aac -shortest clip.ts
2. Serveur : DEMO_MEDIA=clip.ts python3 ../fake-xtream/demo.py 8197   (l'émulateur y accède via http://10.0.2.2:8197)
3. Pour chaque langue (en, fr, ar) : ./fresh.sh LANG (app vierge + source démo + langue) puis ./shoot.sh LANG (écrans via les intents debug_*)
   -> raw/LANG-*.png en 1920×1080 ; puis ../icon/make_screens.py raw ../../../docs/screenshots/LANG LANG
4. Site : python3 ../../../tools/site/build.py
JAMAIS la source réelle : fresh.sh / inject_demo.py ne connaissent que le serveur démo.
