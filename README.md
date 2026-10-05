# Gunz Launcher v3.0 (Android 8+)

Launcher bergaya desktop Windows: desktop + ikon, taskbar dengan penanda aplikasi terbuka,
menu Start, jendela mengambang (Penjelajah File, Peramban, YouTube, Notepad, Kalkulator,
Personalisasi, Pengaturan), tray (Wi-Fi, volume, notifikasi, jam), dukungan keyboard + mouse + sentuh.
Semua ikon digambar sebagai vektor (tanpa emoji).

## Fitur baru v2.0
- **YouTube mengambang** dan **Peramban mengambang** (pencarian Google / ketik alamat web).
  Ketuk tombol panah-keluar di judul jendela untuk memindahkannya ke **atas semua aplikasi**
  (butuh izin "Tampil di atas aplikasi lain"). Bisa digeser, diubah ukuran, minimize jadi gelembung,
  maximize, dan ditutup (tombol close merah seperti Windows).
  Di Pengaturan ada saklar agar keduanya selalu langsung mengambang.
- **Wallpaper dari galeri**: foto atau **video** (diputar berulang tanpa suara).
  Buka Personalisasi (ikon di desktop / klik kanan desktop / Pengaturan).
- Jendela dengan animasi buka/tutup/minimize, tombol close merah saat disorot.
- Taskbar: Penjelajah File, Peramban, YouTube tersemat, garis penanda jendela aktif.
- Penjelajah File meminta izin akses penyimpanan.

## Mengambangkan aplikasi lain (Chrome, WhatsApp, dll)
Android tidak mengizinkan launcher memaksa aplikasi pihak ketiga mengambang. Aktifkan sekali:
Pengaturan > Opsi pengembang > "Aktifkan jendela bentuk bebas" + "Paksa aktivitas dapat diubah
ukurannya", lalu mulai ulang HP. Setelah itu aplikasi yang dibuka dari launcher tampil sebagai jendela.
YouTube & Peramban bawaan tidak butuh langkah ini.

## Cara 1 - Build dari GitHub
1. Buat repository baru di GitHub.
2. Ekstrak ZIP ini, upload SEMUA isinya (termasuk folder `.github`) ke branch `main`.
3. Tab **Actions** -> **Build APK** (otomatis, atau *Run workflow*).
4. Setelah hijau, buka run-nya -> **Artifacts** -> unduh `GunzLauncher-APK` -> pasang `app-debug.apk`.

## Cara 2 - Build di Termux
```
pkg install -y unzip
unzip GunzLauncher.zip && cd GunzLauncher
bash termux-build.sh
```
APK hasil: `~/GunzLauncher.apk`. Syarat: HP ARM64, internet, ruang kosong 2-3 GB, RAM minimal ~3 GB.

## Pemakaian
- Pasang APK, tekan Home, pilih **Gunz Launcher** -> *Selalu*.
- Keluar launcher: Start -> tombol power -> *Keluar dari Launcher*.
- Masuk lagi: buka aplikasi **Gunz Launcher**, atau ketuk tombol Gunz melayang
  (aktifkan lewat logo di taskbar sebelah jam).
- Mouse: klik kanan = menu, klik ganda ikon desktop = buka. Keyboard: Win = Start,
  Win+D = desktop, Win+E = file, Alt+Tab = ganti jendela, Alt+F4 = tutup jendela, Esc = tutup Start.


## Baru di v3.0
- **Wallpaper Bloom biru permanen** (digambar vektor, tidak bisa diganti).
- **Pusat Tindakan** (ikon Wi-Fi/volume/baterai/lonceng di tray): Wi-Fi, Bluetooth, Data, GPS, Volume, Kecerahan.
  Pengaturan di dalam launcher tidak lagi membuka Pengaturan Android (kecuali layar izin yang wajib dari Android).
- **Notifikasi gaya Windows**: beri akses notifikasi lewat Pengaturan > Notifikasi gaya Windows.
- **Kursor mouse Windows**, ikon baterai Windows, label aplikasi `.exe`.
- **Mode aplikasi**: klik kanan ikon > Buka - Jendela / Borderless / Layar penuh. Taskbar auto-hide di Pengaturan.
- **Keymapping** (jendela "Keymapping"): WASD, tembak (klik kiri / F), scope (klik kanan), G gloo, R reload, Spasi lompat, C jongkok,
  mouse-look, sensitivitas, DPI, crosshair, preset per game. Aktifkan layanan "Gunz Keymapping" di Aksesibilitas.
  F8 = nyala/mati, Esc = berhenti. Eksperimental: perlu diuji per game.

