using System;
using System.Collections.Generic;
using UnityEngine;

namespace DummySurfer.Utilities
{
    /// <summary>
    /// Generic component pool (spec 6.3 / 10 POOLING): no Instantiate/Destroy during gameplay,
    /// every pooled object fully reset before reuse.
    /// </summary>
    public sealed class ObjectPool<T> where T : Component
    {
        private readonly Func<T> _factory;
        private readonly Action<T> _onGet;
        private readonly Action<T> _onRelease;
        private readonly Stack<T> _free;
        private readonly Transform _root;

        public int CountInactive => _free.Count;
        public int CreatedTotal { get; private set; }

        public ObjectPool(Transform root, Func<T> factory, Action<T> onGet = null, Action<T> onRelease = null, int prewarm = 0)
        {
            _root = root;
            _factory = factory;
            _onGet = onGet;
            _onRelease = onRelease;
            _free = new Stack<T>(prewarm > 0 ? prewarm : 8);
            for (int i = 0; i < prewarm; i++) _free.Push(Create());
        }

        private T Create()
        {
            var item = _factory();
            item.transform.SetParent(_root, false);
            item.gameObject.SetActive(false);
            CreatedTotal++;
            return item;
        }

        public T Get()
        {
            var item = _free.Count > 0 ? _free.Pop() : Create();
            _onGet?.Invoke(item);
            item.gameObject.SetActive(true);
            return item;
        }

        public void Release(T item)
        {
            if (item == null) return;
            _onRelease?.Invoke(item);
            if (item.transform.parent != _root) item.transform.SetParent(_root, false);
            item.gameObject.SetActive(false);
            _free.Push(item);
        }

        public void Clear()
        {
            _free.Clear();
        }
    }
}
