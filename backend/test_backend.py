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

        # Create subfolder with another image
        cls.sub_dir = os.path.join(cls.test_dir, "sub")
        os.makedirs(cls.sub_dir, exist_ok=True)
        cls.img3_path = os.path.join(cls.sub_dir, "sub_img.png")
        img3 = Image.new('RGB', (100, 100), color = 'blue')
        img3.save(cls.img3_path)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.test_dir, ignore_errors=True)

    def test_1_database_init_and_photos(self):
        photos = database.get_photos()
        self.assertEqual(len(photos), 0)

    def test_2_scan_and_exact_duplicates(self):
        async def mock_scan():
            async def cb(cur, tot, msg):
                pass
            rule_set = {
                "name": "Test Rule",
                "folder_paths": [self.test_dir],
                "file_filter_type": "ALL",
                "include_subfolders": True
            }
            await scanner.scan_folders([rule_set], cb)

        import asyncio
        asyncio.run(mock_scan())

        photos = database.get_photos()
        self.assertEqual(len(photos), 3)

        duplicates = database.get_exact_duplicates()
        self.assertEqual(len(duplicates), 1)
        self.assertEqual(len(duplicates[0]["photos"]), 2)

    def test_3_filter_and_sort_photos(self):
        # Filter by extension
        jpg_photos = database.get_photos(extension="jpg")
        self.assertTrue(all(p["file_path"].endswith(".jpg") for p in jpg_photos))
        self.assertEqual(len(jpg_photos), 2)

        png_photos = database.get_photos(extension=".png")
        self.assertTrue(all(p["file_path"].endswith(".png") for p in png_photos))
        self.assertEqual(len(png_photos), 1)

        # Sort by file_name ASC / DESC
        asc_photos = database.get_photos(sort_by="file_name", order="ASC")
        desc_photos = database.get_photos(sort_by="file_name", order="DESC")
        self.assertEqual(asc_photos[0]["file_path"], desc_photos[-1]["file_path"])

        # Filter by size
        small_photos = database.get_photos(max_size=10)
        self.assertEqual(len(small_photos), 0)
        all_size_photos = database.get_photos(min_size=0)
        self.assertEqual(len(all_size_photos), 3)

if __name__ == "__main__":
    unittest.main()
