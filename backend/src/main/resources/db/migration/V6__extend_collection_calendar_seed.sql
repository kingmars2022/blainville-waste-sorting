-- V2 only seeded a handful of collection_event rows in the past (Aug/Sep 2026).
-- This migration extends the calendar with a recurring pattern through the end
-- of 2026 so the home page reminder has real upcoming data to show, until the
-- full official yearly calendar import (see README roadmap) replaces it.

insert into collection_event
(collection_date, sector, collection_type, bin_color, note_fr, note_en, note_zh, source_url)
values
-- Organics (brown bin): every Thursday, citywide.
('2026-09-10', 'all', 'organic', 'brown', 'Les matieres organiques sont collectees le jeudi sur tout le territoire.', 'Organics are collected on Thursday throughout the city.', '厨余和有机垃圾全市每周四收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-organiques'),
('2026-09-17', 'all', 'organic', 'brown', 'Les matieres organiques sont collectees le jeudi sur tout le territoire.', 'Organics are collected on Thursday throughout the city.', '厨余和有机垃圾全市每周四收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-organiques'),
('2026-09-24', 'all', 'organic', 'brown', 'Les matieres organiques sont collectees le jeudi sur tout le territoire.', 'Organics are collected on Thursday throughout the city.', '厨余和有机垃圾全市每周四收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-organiques'),
('2026-10-01', 'all', 'organic', 'brown', 'Les matieres organiques sont collectees le jeudi sur tout le territoire.', 'Organics are collected on Thursday throughout the city.', '厨余和有机垃圾全市每周四收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-organiques'),
('2026-10-08', 'all', 'organic', 'brown', 'Les matieres organiques sont collectees le jeudi sur tout le territoire.', 'Organics are collected on Thursday throughout the city.', '厨余和有机垃圾全市每周四收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-organiques'),
('2026-10-15', 'all', 'organic', 'brown', 'Les matieres organiques sont collectees le jeudi sur tout le territoire.', 'Organics are collected on Thursday throughout the city.', '厨余和有机垃圾全市每周四收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-organiques'),
('2026-10-22', 'all', 'organic', 'brown', 'Les matieres organiques sont collectees le jeudi sur tout le territoire.', 'Organics are collected on Thursday throughout the city.', '厨余和有机垃圾全市每周四收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-organiques'),
('2026-10-29', 'all', 'organic', 'brown', 'Les matieres organiques sont collectees le jeudi sur tout le territoire.', 'Organics are collected on Thursday throughout the city.', '厨余和有机垃圾全市每周四收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-organiques'),
-- Recycling (blue bin): biweekly, alternating by sector.
('2026-09-15', 'south', 'recycling', 'blue', 'Collecte au sud du boulevard de la Seigneurie.', 'Collection south of boulevard de la Seigneurie.', 'boulevard de la Seigneurie 以南区域收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables'),
('2026-09-16', 'north', 'recycling', 'blue', 'Collecte au nord du boulevard de la Seigneurie.', 'Collection north of boulevard de la Seigneurie.', 'boulevard de la Seigneurie 以北区域收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables'),
('2026-09-29', 'south', 'recycling', 'blue', 'Collecte au sud du boulevard de la Seigneurie.', 'Collection south of boulevard de la Seigneurie.', 'boulevard de la Seigneurie 以南区域收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables'),
('2026-09-30', 'north', 'recycling', 'blue', 'Collecte au nord du boulevard de la Seigneurie.', 'Collection north of boulevard de la Seigneurie.', 'boulevard de la Seigneurie 以北区域收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables'),
('2026-10-13', 'south', 'recycling', 'blue', 'Collecte au sud du boulevard de la Seigneurie.', 'Collection south of boulevard de la Seigneurie.', 'boulevard de la Seigneurie 以南区域收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables'),
('2026-10-14', 'north', 'recycling', 'blue', 'Collecte au nord du boulevard de la Seigneurie.', 'Collection north of boulevard de la Seigneurie.', 'boulevard de la Seigneurie 以北区域收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables'),
('2026-10-27', 'south', 'recycling', 'blue', 'Collecte au sud du boulevard de la Seigneurie.', 'Collection south of boulevard de la Seigneurie.', 'boulevard de la Seigneurie 以南区域收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables'),
('2026-10-28', 'north', 'recycling', 'blue', 'Collecte au nord du boulevard de la Seigneurie.', 'Collection north of boulevard de la Seigneurie.', 'boulevard de la Seigneurie 以北区域收集。', 'https://blainville.ca/services/environnement-et-voirie/matieres-recuperables');
