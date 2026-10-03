import unittest
import os
import shutil
import tempfile

# Set environment variable before importing database
test_dir = tempfile.mkdtemp()
db_path = os.path.join(test_dir, "test.db")
os.environ["PHOTO_ORGANIZER_DB"] = db_path

from PIL import Image
import database
import scanner

class TestBackend(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.test_dir = test_dir
        cls.db_path = db_path
        database.init_db()

        # Create dummy image
        cls.img1_path = os.path.join(cls.test_dir, "test1.jpg")
        img1 = Image.new('RGB', (100, 100), color = 'red')
        img1.save(cls.img1_path)

        # Create duplicate image
        cls.img2_path = os.path.join(cls.test_dir, "test2.jpg")
        shutil.copy(cls.img1_path, cls.img2_path)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.test_dir, ignore_errors=True)

    def test_database_init_and_photos(self):
        photos = database.get_photos()
        self.assertEqual(len(photos), 0)

    def test_scan_and_exact_duplicates(self):
        async def mock_scan():
            async def cb(cur, tot, msg):
                pass
            await scanner.scan_folders([self.test_dir], cb)

        import asyncio
        asyncio.run(mock_scan())

        photos = database.get_photos()
        self.assertEqual(len(photos), 2)

        duplicates = database.get_exact_duplicates()
        self.assertEqual(len(duplicates), 1)
        self.assertEqual(len(duplicates[0]["photos"]), 2)

if __name__ == "__main__":
    unittest.main()
