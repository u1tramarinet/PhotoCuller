# Photo Organizer Windows Application

写真整理 Windows アプリケーション（フロントエンド: Kotlin + Compose Multiplatform / バックエンド: Python + FastAPI + OpenCV）

## 概要
PC内の複数のフォルダに散在する大量の写真データを横断的に一括管理し、画像処理および機械学習技術を用いて「重複・類似写真の検出」「ピンボケ・ブレ写真の検出」「類似画角・シーンごとのグルーピング」を自動化するWindowsデスクトップアプリケーションです。

---

## 主な機能
- **フォルダ管理・スキャン**: 複数のフォルダパスを登録・監視し、画像ファイル（JPEG, PNG, WebP等）を差分スキャン。
- **リアルタイム進捗同期**: WebSocket経由でスキャン進捗（処理枚数、ステータス）をUIプログレスバーにリアルタイム表示。
- **写真ギャラリー**: `LazyVerticalGrid` による高速な仮想化グリッド表示。
- **重複検出**: SHA-256暗号ハッシュを用いたバイナリ完全一致写真の自動検出。
- **ブレ・ピンボケ検出**: OpenCVラプラシアンフィルタの分散値（Variance of Laplacian）によるブレスコア計算および抽出。
- **インスペクターパネル**: 選択した写真のメタデータ（ファイルパス、撮影日時、解像度、ブレスコア、ハッシュ等）表示とゴミ箱移動機能。

---

## 開発・動作手順

### 動作環境
- **Java**: JDK 21 以上
- **Python**: Python 3.10 以上 (Python 3.12推奨)

### 1. バックエンド依存ライブラリのインストール
```bash
pip install -r backend/requirements.txt
```

### 2. アプリケーションのビルドと起動

Gradle Wrapper を用いてアプリをビルド・起動できます。起動時に Kotlin アプリが Python バックエンドプロセスを自動スタートします。

```bash
./gradlew run
```

### 3. テストの実行

#### Kotlin UI / サービス テスト
```bash
./gradlew test
```

#### Python バックエンド テスト
```bash
PYTHONPATH=backend python3 -m unittest backend/test_backend.py
```
