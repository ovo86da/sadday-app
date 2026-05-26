import 'package:flutter/material.dart';
import '../../core/api/paged_response.dart';
import 'app_empty_state.dart';

typedef PageLoader<T> = Future<PagedResponse<T>> Function(int page);

class AppPagedList<T> extends StatefulWidget {
  const AppPagedList({
    required this.loader,
    required this.itemBuilder,
    this.padding,
    this.emptyMessage = 'Sin resultados',
    super.key,
  });

  final PageLoader<T> loader;
  final Widget Function(BuildContext context, T item, int index) itemBuilder;
  final EdgeInsetsGeometry? padding;
  final String emptyMessage;

  @override
  State<AppPagedList<T>> createState() => _AppPagedListState<T>();
}

class _AppPagedListState<T> extends State<AppPagedList<T>> {
  final List<T> _items = [];
  final _scrollController = ScrollController();
  int _nextPage = 0;
  bool _hasMore = true;
  bool _loading = false;
  bool _initialLoading = true;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    _load(0);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    final pos = _scrollController.position;
    if (pos.pixels >= pos.maxScrollExtent - 300 && !_loading && _hasMore) {
      _load(_nextPage);
    }
  }

  Future<void> _load(int page) async {
    if (_loading) return;
    setState(() => _loading = true);
    try {
      final res = await widget.loader(page);
      if (!mounted) return;
      setState(() {
        if (page == 0) _items.clear();
        _items.addAll(res.items);
        _hasMore = !res.isLast;
        _nextPage = page + 1;
        _error = null;
        _initialLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _initialLoading = false;
      });
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_initialLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null && _items.isEmpty) {
      return AppEmptyState(
        message: 'Error al cargar',
        description: _error.toString(),
        icon: Icons.error_outline,
        actionLabel: 'Reintentar',
        onAction: () => _load(0),
      );
    }

    return RefreshIndicator(
      onRefresh: () => _load(0),
      child: _items.isEmpty
          ? CustomScrollView(
              controller: _scrollController,
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                SliverFillRemaining(
                    child: AppEmptyState(message: widget.emptyMessage)),
              ],
            )
          : ListView.builder(
              controller: _scrollController,
              padding: widget.padding ?? const EdgeInsets.all(16),
              itemCount: _items.length + (_loading ? 1 : 0),
              itemBuilder: (ctx, i) {
                if (i >= _items.length) {
                  return const Padding(
                    padding: EdgeInsets.symmetric(vertical: 16),
                    child: Center(
                        child: CircularProgressIndicator(strokeWidth: 2)),
                  );
                }
                return widget.itemBuilder(ctx, _items[i], i);
              },
            ),
    );
  }
}
