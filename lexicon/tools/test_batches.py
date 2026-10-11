"""batches.py reads the candidates of `ime-dict-tool words` by their header's names (Main.kt,
CandidateRow.HEADER), so a column moved or added misleads it in no column, and refuses candidates
of another layout."""
import contextlib
import io
import os
import tempfile
import unittest
from unittest import mock

import batches

# as `words` writes them: year between count and pmi, where pmi read by its place would be the year
CANDIDATES = ('# text\tcount\tyear\tpmi\tleft_entropy\tright_entropy\tsurprise\tknown\n'
              '# chars\t100000\n'
              '内卷\t900\t2024\t5.5\t2.10\t3.00\t1.0\t0\n'
              '婠婠\t50\t2023\t7.25\t1.50\t2.50\t2.0\t0\n'
              '砍一刀\t300\t2024\t4.0\t2.00\t2.00\t1.0\t0\n')
PACK = "# youmo words 1\n内卷\tnei'juan\t-3.0\n婠婠\tguan'guan\t-4.0\n砍一刀\tkan'yi'dao\t-4.5\n"
# what the engine types for each of the pack's readings, in its order: 砍一刀 right alone
PROBE = 'neijuan\t100\t内眷\t内卷\nguanguan\t90\t关关\nkanyidao\t80\t砍一刀\n'
EXAMPLES = '内卷\t大家都在内卷\n'


class BatchesTest(unittest.TestCase):

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.dir = tmp.name

    def write(self, name, text):
        path = os.path.join(self.dir, name)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(text)
        return path

    def first_batch(self, candidates):
        """batches.py over these candidates and the pack above, with nothing decided: batch-001.tsv."""
        out = os.path.join(self.dir, 'out')
        argv = ['batches.py', '--pack', self.write('pack.words', PACK),
                '--candidates', self.write('candidates.tsv', candidates),
                '--probe', self.write('probe.tsv', PROBE), '--examples', self.write('examples.tsv', EXAMPLES),
                '--out', out, '--lexicon', self.dir]
        with mock.patch('sys.argv', argv), contextlib.redirect_stdout(io.StringIO()):
            batches.main()
        with open(os.path.join(out, 'batch-001.tsv'), encoding='utf-8') as f:
            return f.read()

    def test_the_candidates_are_read_by_their_headers_names(self):
        self.assertEqual('# word\treading\tcount\tpmi\tentropy\tengine\texamples...\n'
                         "内卷\tnei'juan\t900\t5.5\t2.10\t内眷/内卷\t大家都在内卷\n"
                         "婠婠\tguan'guan\t50\t7.25\t1.50\t关关\n", self.first_batch(CANDIDATES))

    def test_candidates_of_another_layout_are_refused(self):
        with self.assertRaises(SystemExit) as raised:
            self.first_batch('# text\tcount\tpmi\n内卷\t900\t5.5\n')
        self.assertIn('not the candidates of ime-dict-tool words', str(raised.exception))
