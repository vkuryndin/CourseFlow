import {describe,it,expect} from 'vitest';
import {reviewCount,courseCount,dayCount,coverKind,initials} from '../src/shared/visual-utils';
describe('Course visuals and Russian labels',()=>{
 it('uses correct Russian plural forms',()=>{expect([0,1,2,5,11,21,22,25,111].map(reviewCount)).toEqual(['0 отзывов','1 отзыв','2 отзыва','5 отзывов','11 отзывов','21 отзыв','22 отзыва','25 отзывов','111 отзывов']);expect(courseCount(3)).toBe('3 курса');expect(dayCount(1)).toBe('1 день');expect(dayCount(24)).toBe('24 дня');expect(dayCount(30)).toBe('30 дней')});
 it('selects covers by category instead of numeric id',()=>{expect(coverKind('Java Programming')).toBe('programming');expect(coverKind('Базы данных')).toBe('database');expect(coverKind('Cloud & DevOps')).toBe('infrastructure');expect(coverKind('Data Science')).toBe('analytics');expect(coverKind('History')).toBe('education');expect(coverKind(null)).toBe('education')});
 it('derives initials from actual name',()=>{expect(initials('  Enrollment Test Teacher ')).toBe('ET');expect(initials('Иван Петров')).toBe('ИП');expect(initials('')).toBe('?')});
});
